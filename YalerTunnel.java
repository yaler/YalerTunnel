// Copyright (c) 2010, Oberon microsystems AG, Switzerland
// All rights reserved

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

class YalerTunnel {
	static final Object
		CLIENT = 'c', SERVER = 's', PROXY = 'p',
		WRITING_REQUEST = 0, READING_RESPONSE = 1,
		READING_REQUEST = 2, WRITING_RESPONSE = 3, RELAYING = 4;
	static final byte[]
		CONNECT = encode("CONNECT "),
		HTTP101 = encode("HTTP/1.1 101"),
		HTTP200 = encode("HTTP/1.1 200"),
		HTTP204 = encode("HTTP/1.1 204"),
		HTTP_OK = encode("HTTP/1.1 200 OK\r\n\r\n");

	static Selector selector;
	static Object mode;
	static InetSocketAddress localEndpoint, yalerEndpoint;
	static byte[] request;

	static byte[] encode (String s) {
		return s.getBytes(Charset.forName("US-ASCII"));
	}

	static void openSelector () {
		try {
			selector = Selector.open();
		} catch (Exception e) { throw new Error(e); }
	}

	static void select () {
		try {
			selector.select();
		} catch (Exception e) { throw new Error(e); }
	}

	static Object mode (String s) {
		Object m = s.charAt(0);
		if ((m == CLIENT) || (m == SERVER) || (m == PROXY)) {
			return m;
		} else {
			throw new IllegalArgumentException(s);
		}
	}

	static InetSocketAddress endpoint (String s) {
		String[] t = s.split(":");
		try {
			return new InetSocketAddress(t[0], Integer.parseInt(t[1]));
		} catch (Exception e) { throw new Error(e); }
	}

	static ByteBuffer allocateReceiveBuffer (SocketChannel c) {
		try {
			return ByteBuffer.allocateDirect(c.socket().getReceiveBufferSize());
		} catch (Exception e) { throw new Error(e); }
	}

	static void shutdownOutput (SocketChannel c) {
		try {
			c.socket().shutdownOutput();
		} catch (Exception e) {}
	}

	static void close (SocketChannel c) {
		try {
			c.close();
		} catch (Exception e) {}
	}

	static SocketChannel channel (SelectionKey k) {
		return (SocketChannel) k.channel();
	}

	static Object[] attachment (SelectionKey k) {
		return (Object[]) k.attachment();
	}

	static Object state (SelectionKey k) {
		return attachment(k)[0];
	}

	static void setState (SelectionKey k, Object s) {
		attachment(k)[0] = s;
	}

	static InetSocketAddress endpoint (SelectionKey k) {
		return (InetSocketAddress) attachment(k)[1];
	}

	static SelectionKey peer (SelectionKey k) {
		return (SelectionKey) attachment(k)[2];
	}

	static void setPeer (SelectionKey k, SelectionKey p) {
		attachment(k)[2] = p;
	}

	static ByteBuffer buffer (SelectionKey k) {
		return (ByteBuffer) attachment(k)[3];
	}

	static void setBuffer (SelectionKey k, ByteBuffer b) {
		attachment(k)[3] = b;
	}

	static void include (SelectionKey k, int ops) {
		k.interestOps(k.interestOps() | ops);
	}

	static void exclude (SelectionKey k, int ops) {
		k.interestOps(k.interestOps() & ~ops);
	}

	static boolean startsWith (ByteBuffer b, byte[] p) {
		int i = 0, j = p.length;
		if (j <= b.limit()) {
			while ((i != j) && (b.get(i) == p[i])) {
				i++;
			}
		}
		return i == j;
	}

	static int endOfHeaders (ByteBuffer b, int i, int j) {
		final int CR = 1, CRLF = 2, CRLF_CR = 3, CRLF_CRLF = 4;
		int state = 0;
		while ((i != j) && (state != CRLF_CRLF)) {
			byte x = b.get(i);
			if (state == 0) {
				if (x == '\r') {
					state = CR;
				}
			} else if (state == CR) {
				if (x == '\n') {
					state = CRLF;
				} else if (x != '\r') {
					state = 0;
				}
			} else if (state == CRLF) {
				if (x == '\r') {
					state = CRLF_CR;
				} else {
					state = 0;
				}
			} else {
				assert state == CRLF_CR;
				if (x == '\n') {
					state = CRLF_CRLF;
				} else if (x == '\r') {
					state = CR;
				} else {
					state = 0;
				}
			}
			i++;
		}
		return state == CRLF_CRLF? i: -1;
	}

	static void openServer (InetSocketAddress a) {
		try {
			ServerSocketChannel c = ServerSocketChannel.open();
			c.configureBlocking(false);
			c.socket().setReuseAddress(true);
			c.socket().bind(a, 64);
			c.register(selector, SelectionKey.OP_ACCEPT);
		} catch (Exception e) { throw new Error(e); }
	}

	static void open (InetSocketAddress a, SelectionKey peer) {
		try {
			SocketChannel c = SocketChannel.open();
			c.configureBlocking(false);
			c.socket().setTcpNoDelay(true);
			c.connect(a);
			c.register(selector, SelectionKey.OP_CONNECT,
				new Object[] {null, a, peer, allocateReceiveBuffer(c)});
		} catch (Exception e) { throw new Error(e); }
	}

	static void reset (SelectionKey k) {
		open(endpoint(k), peer(k));
		close(channel(k));
	}

	static void accept (SelectionKey k) {
		try {
			ServerSocketChannel s = (ServerSocketChannel) k.channel();
			SocketChannel c = s.accept();
			c.configureBlocking(false);
			c.socket().setTcpNoDelay(true);
			c.register(selector, 0,
				new Object[] {null, null, null, allocateReceiveBuffer(c)});
			open(yalerEndpoint, c.keyFor(selector));
		} catch (Exception e) { throw new Error(e); }
	}

	static void beginWriting (SelectionKey k, byte[] b, Object state) {
		buffer(k).clear();
		buffer(k).put(b).flip();
		include(k, SelectionKey.OP_WRITE);
		setState(k, state);
	}

	static void beginRelaying (SelectionKey k) {
		SelectionKey p = peer(k);
		include(k, SelectionKey.OP_READ);
		if (buffer(p).position() == 0) {
			include(p, SelectionKey.OP_READ);
		} else {
			buffer(p).flip();
			include(k, SelectionKey.OP_WRITE);
		}
		setState(p, RELAYING);
		setState(k, RELAYING);
	}

	static void connect (SelectionKey k) {
		SelectionKey p = peer(k);
		SocketChannel c = channel(k);
		try {
			c.finishConnect();
		} catch (Exception e) {}
		if (c.isConnected()) {
			if (p != null) {
				setPeer(p, k);
			}
			exclude(k, SelectionKey.OP_CONNECT);
			if ((mode == CLIENT) || (p == null)) {
				beginWriting(k, request, WRITING_REQUEST);
			} else {
				assert (mode == SERVER) || (mode == PROXY);
				beginRelaying(k);
			}
		} else {
			reset(k);
		}
	}

	static void switchProtocol (SelectionKey k) {
		open(yalerEndpoint, null);
		if (mode == SERVER) {
			exclude(k, SelectionKey.OP_READ);
			open(localEndpoint, k);
		} else {
			assert mode == PROXY;
			ByteBuffer b = buffer(k);
			int l = endOfHeaders(b, 0, b.position());
			if (l != -1) {
				if (startsWith(b, CONNECT) && (l == b.position())) {
					exclude(k, SelectionKey.OP_READ);
					beginWriting(k, HTTP_OK, WRITING_RESPONSE);
				} else {
					reset(k);
				}
			} else {
				setState(k, READING_REQUEST);
			}
		}
	}

	static void handleBuffer (SelectionKey k) {
		ByteBuffer b = buffer(k);
		int l = endOfHeaders(b, 0, b.position());
		if (l != -1) {
			if (state(k) == READING_RESPONSE) {
				if (mode == CLIENT) {
					if (startsWith(b, HTTP200) && (l == b.position())) {
						b.clear();
						beginRelaying(k);
					} else {
						reset(k);
					}
				} else {
					assert (mode == SERVER) || (mode == PROXY);
					if (startsWith(b, HTTP101)) {
						b.limit(b.position());
						b.position(l);
						b.compact();
						switchProtocol(k);
					} else if (startsWith(b, HTTP204) && (l == b.position())) {
						exclude(k, SelectionKey.OP_READ);
						beginWriting(k, request, WRITING_REQUEST);
					} else {
						reset(k);
					}
				}
			} else {
				assert (state(k) == READING_REQUEST) && (mode == PROXY);
				if (startsWith(b, CONNECT) && (l == b.position())) {
					exclude(k, SelectionKey.OP_READ);
					beginWriting(k, HTTP_OK, WRITING_RESPONSE);
				} else {
					reset(k);
				}
			}
		} else if (!b.hasRemaining()) {
			reset(k);
		}
	}

	static void read (SelectionKey k) {
		SelectionKey p = peer(k);
		SocketChannel c = channel(k);
		ByteBuffer b = buffer(k);
		int n = -1;
		try {
			n = c.read(b);
		} catch (Exception e) {}
		if (n > 0) {
			if (state(k) != RELAYING) {
				handleBuffer(k);
			} else {
				b.flip();
				exclude(k, SelectionKey.OP_READ);
				include(p, SelectionKey.OP_WRITE);
			}
		} else if (n == -1) {
			if (state(k) != RELAYING) {
				reset(k);
			} else {
				if (buffer(p) != null) {
					exclude(k, SelectionKey.OP_READ);
					setBuffer(k, null);
					shutdownOutput(channel(p));
				} else {
					close(channel(p));
					close(c);
				}
			}
		}
	}

	static void write (SelectionKey k) {
		SelectionKey p = peer(k);
		SocketChannel c = channel(k);
		ByteBuffer b = buffer(state(k) != RELAYING? k: p);
		int n = -1;
		try {
			n = c.write(b);
		} catch (Exception e) {}
		if (!b.hasRemaining()) {
			b.clear();
			exclude(k, SelectionKey.OP_WRITE);
			if (state(k) == WRITING_REQUEST) {
				include(k, SelectionKey.OP_READ);
				setState(k, READING_RESPONSE);
			} else if (state(k) == WRITING_RESPONSE) {
				assert mode == PROXY;
				open(localEndpoint, k);
			} else {
				assert state(k) == RELAYING;
				include(p, SelectionKey.OP_READ);
			}
		} else if (n == -1) {
			if (state(k) != RELAYING) {
				reset(k);
			} else {
				if (buffer(k) != null) {
					exclude(k, SelectionKey.OP_WRITE);
					setBuffer(p, null);
				} else {
					close(channel(p));
					close(c);
				}
			}
		}
	}

	public static void main (String[] args) {
		if (args.length == 0) {
			System.err.print("YalerTunnel 1.0\n"
				+ "Usage: YalerTunnel (c | s | p) <local host>:<port> "
				+ "<yaler host>:<port> <yaler domain>\n");
		} else {
			openSelector();
			mode = mode(args[0]);
			localEndpoint = endpoint(args[1]);
			yalerEndpoint = endpoint(args[2]);
			if (mode == CLIENT) {
				request = encode(
					"CONNECT /" + args[3] + " HTTP/1.1\r\n" +
					"Host: " + args[2].split(":")[0] + "\r\n\r\n");
				openServer(localEndpoint);
			} else {
				assert (mode == SERVER) || (mode == PROXY);
				request = encode(
					"POST /" + args[3] + " HTTP/1.1\r\n" +
					"Upgrade: PTTH/1.0\r\n" +
					"Connection: Upgrade\r\n" +
					"Host: " + args[2].split(":")[0] + "\r\n\r\n");
				open(yalerEndpoint, null);
			}
			while (true) {
				select();
				for (SelectionKey k: selector.selectedKeys()) {
					if (k.isValid() && k.isAcceptable()) {
						accept(k);
					}
					if (k.isValid() && k.isConnectable()) {
						connect(k);
					}
					if (k.isValid() && k.isReadable()) {
						read(k);
					}
					if (k.isValid() && k.isWritable()) {
						write(k);
					}
				}
				selector.selectedKeys().clear();
			}
		}
	}
}
