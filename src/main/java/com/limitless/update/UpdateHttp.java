package com.limitless.update;

import com.limitless.LimitlessClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;

/**
 * 更新专用的 HTTP 客户端。
 * <p>
 * 为什么不能直接用默认的 {@code HttpClient}：Java 只认 JDK 自带 {@code cacerts}，而
 * <b>加速器 / 抓包工具会把流量接管并换上自己的根证书</b>（典型如 Steam++ / Watt Toolkit，
 * 证书主体 {@code CN=SteamTools Certificate}）。这类根证书被装进 Windows 证书库，浏览器因此能用，
 * 但 Java 默认看不到，于是握手直接抛
 * {@code PKIX path building failed}，检查被当作「无更新」静默放行——玩家永远等不到提示。
 * <p>
 * 这里的策略是：<b>先按 JDK 默认校验，校验不通过时才回退到系统（Windows）根证书库</b>，
 * 且只在更新这一路流量上生效，不改任何全局 SSL 配置。回退一旦发生会打一条明显的 WARN，
 * 因为这意味着连接可能经过本机中间人。
 */
public final class UpdateHttp {
	private static volatile SSLContext context;
	private static volatile Boolean contextReady;
	private static boolean warnedAboutOsStore;

	private UpdateHttp() {
	}

	public static HttpClient newClient(Duration connectTimeout) {
		HttpClient.Builder builder = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.followRedirects(HttpClient.Redirect.NORMAL);
		SSLContext ctx = context();
		if (ctx != null) {
			builder.sslContext(ctx);
		}
		return builder.build();
	}

	private static SSLContext context() {
		if (Boolean.TRUE.equals(contextReady)) {
			return context;
		}
		synchronized (UpdateHttp.class) {
			if (Boolean.TRUE.equals(contextReady)) {
				return context;
			}
			try {
				SSLContext built = SSLContext.getInstance("TLS");
				built.init(null, new TrustManager[]{composite()}, null);
				context = built;
			} catch (Throwable t) {
				LimitlessClient.LOGGER.warn("[Limitless] 自定义信任链初始化失败，退回 JDK 默认", t);
				context = null;
			}
			contextReady = Boolean.TRUE;
			return context;
		}
	}

	private static X509ExtendedTrustManager composite() throws Exception {
		X509ExtendedTrustManager primary = fromStore(null);
		X509ExtendedTrustManager system = fromWindowsRoot();
		if (system == null) {
			return primary;
		}
		return new X509ExtendedTrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				primary.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
					throws CertificateException {
				primary.checkClientTrusted(chain, authType, socket);
			}

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
					throws CertificateException {
				primary.checkClientTrusted(chain, authType, engine);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				check(chain, authType, null, null);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
					throws CertificateException {
				check(chain, authType, socket, null);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
					throws CertificateException {
				check(chain, authType, null, engine);
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return primary.getAcceptedIssuers();
			}

			private void check(X509Certificate[] chain, String authType, Socket socket, SSLEngine engine)
					throws CertificateException {
				try {
					if (engine != null) {
						primary.checkServerTrusted(chain, authType, engine);
					} else if (socket != null) {
						primary.checkServerTrusted(chain, authType, socket);
					} else {
						primary.checkServerTrusted(chain, authType);
					}
					return;
				} catch (CertificateException primaryFailure) {
					try {
						if (engine != null) {
							system.checkServerTrusted(chain, authType, engine);
						} else if (socket != null) {
							system.checkServerTrusted(chain, authType, socket);
						} else {
							system.checkServerTrusted(chain, authType);
						}
					} catch (CertificateException systemFailure) {
						// 两套都不认：抛出原始错误，便于排查到底是证书还是网络
						throw primaryFailure;
					}
					synchronized (UpdateHttp.class) {
						if (!warnedAboutOsStore) {
							warnedAboutOsStore = true;
							LimitlessClient.LOGGER.warn("[Limitless] 更新连接使用系统证书库完成校验"
									+ "（检测到本机中间人证书，通常是加速器 / 抓包工具，如 Steam++）。"
									+ "下载内容来自 GitHub Releases，仍建议核对版本号。");
						}
					}
				}
			}
		};
	}

	/** {@code store} 为 null 时用自己的默认信任库（JDK cacerts）。 */
	private static X509ExtendedTrustManager fromStore(KeyStore store) throws Exception {
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(store);
		for (TrustManager manager : factory.getTrustManagers()) {
			if (manager instanceof X509ExtendedTrustManager extended) {
				return extended;
			}
			if (manager instanceof X509TrustManager plain) {
				return wrap(plain);
			}
		}
		throw new IllegalStateException("没有可用的 X509 信任管理器");
	}

	private static X509ExtendedTrustManager wrap(X509TrustManager plain) {
		return new X509ExtendedTrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				plain.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
					throws CertificateException {
				plain.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
					throws CertificateException {
				plain.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				plain.checkServerTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
					throws CertificateException {
				plain.checkServerTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
					throws CertificateException {
				plain.checkServerTrusted(chain, authType);
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return plain.getAcceptedIssuers();
			}
		};
	}

	/** 非 Windows 平台没有 {@code Windows-ROOT}，返回 null 即只用默认校验。 */
	private static X509ExtendedTrustManager fromWindowsRoot() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (!os.contains("win")) {
			return null;
		}
		try {
			KeyStore store = KeyStore.getInstance("Windows-ROOT");
			store.load(null, null);
			return fromStore(store);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 读取 Windows 根证书库失败（仍按默认校验）: {}", t.toString());
			return null;
		}
	}
}
