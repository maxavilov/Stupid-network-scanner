/*
 * Copyright (C) 2017 Maxim Avilov (maxim.avilov@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
 * OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * +++++++++++++[>+>+++++>+++++++++>++++++++>++>++++++<<<<<<-]>>.>.-.>.<-----.++
 * +.<-------.>>>++++++.>-.<<-------.<++++++.>++++++++.++++.>.<<<+++++++.>--.>--
 * --.+++.+++.<.<<---.
 */
package ru.amn.networkscanner.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.json.JSONObject;

import ru.amn.networkscanner.shared.NetworkScannerConsts;

@ServerEndpoint(NetworkScannerConsts.SCANNER_ENDPOINT)
public class NetworkScannerWebSocketService {

	// Если узел не ответил за это время, то считаем его мертвым
	private static final int PING_TIMEOUT = 3000;

	// Здесь храним сессии и потоки, которые их обрабатывают
	private static final ConcurrentMap<Session, ScanerThread> peers = new ConcurrentHashMap<>();

	@OnMessage
	public void onMessage(Session session, String networkAddress) {
		// Создаем кандидата на обработку запросов от сессии
		// и вытаемся его вставить в map. Если обработчик сессии уже существует,
		// то sessionProcessor пойдет в мусор, если нет - то мы стартуем его.
		ScanerThread sessionProcessor = new ScanerThread(session);
		ScanerThread currentSessionProcessor = peers.putIfAbsent(session, sessionProcessor);
		if (currentSessionProcessor == null) {
			sessionProcessor.start();
			currentSessionProcessor = sessionProcessor;
		}
		try {
			// Ставим запрос в очередь обработчика
			currentSessionProcessor.newRequest(networkAddress);
		} catch (InterruptedException e) {

		}
	}

	@OnClose
	public void onClose(Session session, CloseReason reason) {
		ScanerThread currentSessionProcessor = peers.remove(session);
		if (currentSessionProcessor != null) {
			currentSessionProcessor.setNeedTerminateToTrue();
		}
	}

	private class ScanerThread extends Thread {
		// Разные сессии обрабатываются в разных потоках
		private Session session;
		private volatile boolean needTerminate = false;
		// Очередь запросов от сессии
		private BlockingQueue<String> requests = new SynchronousQueue<>();

		public ScanerThread(Session session) {
			this.session = session;
		}

		public void setNeedTerminateToTrue() {
			needTerminate = true;
		}

		public void newRequest(String networkAddress) throws InterruptedException {
			requests.put(networkAddress);
		}

		public void run() {
			while (!needTerminate) {
				try {
					String request = requests.poll(2, TimeUnit.SECONDS);
					if (request != null) {
						// Создаем коллекцию валидных адресов для сети
						List<String> addressCollection = new ArrayList<>(253);
						for (int i = 1; i < 255; i++) {
							addressCollection.add(request + i);
						}
						// И запускаем её парралельную обработку
						addressCollection.parallelStream().forEach(address -> processAddress(address));
						// Вся коллекция обработана, сообщаем об этом клиенту
						synchronized (session) {
							try {
								if (session.isOpen())
									session.getBasicRemote().sendText(NetworkScannerConsts.SCAN_COMPLETE);
							} catch (IOException e) {

							}
						}
					}
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		// Обработка отдельных адресов
		private void processAddress(String address) {
			Long startTime = System.currentTimeMillis();
			try {
				if (!needTerminate && InetAddress.getByName(address).isReachable(PING_TIMEOUT)) {
					// Адрес ответил на ICMP ECHO пакет
					Long endTime = System.currentTimeMillis();
					JSONObject response = new JSONObject();
					response.put(NetworkScannerConsts.KEY_HOST, address);
					response.put(NetworkScannerConsts.KEY_DELAY, endTime - startTime);
					synchronized (session) {
						if (session.isOpen())
							session.getBasicRemote().sendText(response.toString());
					}
				}
			} catch (UnknownHostException e) {

			} catch (IOException e) {

			}
		}
	}
}
