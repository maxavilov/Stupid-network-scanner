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
package ru.amn.networkscanner.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.sksamuel.gwt.websockets.Websocket;
import com.sksamuel.gwt.websockets.WebsocketListener;

import ru.amn.networkscanner.shared.NetworkAddressVerifier;
import ru.amn.networkscanner.shared.NetworkScannerConsts;

public class NetworkScanner implements EntryPoint {

	private boolean canScan = true;

	private TextBox createAddressComponentTextBox() {
		TextBox result = new TextBox();
		result.setMaxLength(3);
		result.setWidth("3em");
		RootPanel.get("networkAddressInputContainer").add(result);
		RootPanel.get("networkAddressInputContainer").add(new InlineLabel("."));
		return result;
	}

	public void onModuleLoad() {
		final TextBox netAddressFiled1 = createAddressComponentTextBox();
		final TextBox netAddressFiled2 = createAddressComponentTextBox();
		final TextBox netAddressFiled3 = createAddressComponentTextBox();
		netAddressFiled1.setFocus(true);
		RootPanel.get("networkAddressInputContainer").add(new InlineLabel("0/24"));
		final Button scanButton = new Button("Scan");
		RootPanel.get("networkAddressSendContainer").add(scanButton);
		final Label netAddrErrorLabel = new Label();
		RootPanel.get("networkArrdessErrorLabelContainer").add(netAddrErrorLabel);
		final FlexTable resultTable = new FlexTable();
		resultTable.setText(0, 0, "Host");
		resultTable.setText(0, 1, "Delay (ms)");
		RootPanel.get("scanResults").add(resultTable);
		final Label scanStatusLabel = new Label();
		RootPanel.get("scanStatus").add(scanStatusLabel);

		String protocol;
		if (Window.Location.getProtocol().toLowerCase().startsWith("https")) {
			protocol = "wss://";
		} else {
			protocol = "ws://";
		}
		// Websocket для сканера (на другом конце слушает
		// NetworkScannerWebSocketService)
		Websocket socket = new Websocket(protocol + Window.Location.getHostName() + ":" + Window.Location.getPort()
				+ NetworkScannerConsts.SCANNER_ENDPOINT);

		socket.addListener(new WebsocketListener() {

			@Override
			public void onMessage(String response) {
				if (response.equals(NetworkScannerConsts.SCAN_COMPLETE)) {
					scanStatusLabel.setText("Scaning is complete!");
					scanButton.setEnabled(true);
					canScan = true;
				} else {
					JSONValue jsonValue = JSONParser.parseStrict(response);
					JSONObject addressAndDelay = jsonValue.isObject();
					if (addressAndDelay != null && addressAndDelay != null) {
						JSONString address = addressAndDelay.get(NetworkScannerConsts.KEY_HOST).isString();
						JSONNumber delay = addressAndDelay.get(NetworkScannerConsts.KEY_DELAY).isNumber();
						int row = resultTable.getRowCount();
						resultTable.setText(row, 0, address.stringValue());
						resultTable.setText(row, 1, new Integer((int) delay.doubleValue()).toString());
					}
				}
			}

			@Override
			public void onOpen() {

			}

			@Override
			public void onClose() {

			}
		});

		// Websocket просто открывается. Ошибки и обрывы соединения здесь не
		// обрабатываются
		socket.open();

		class NetworkAddressHandler implements ValueChangeHandler<String>, ClickHandler, KeyUpHandler {
			@Override
			public void onValueChange(ValueChangeEvent<String> newVal) {
				if (NetworkAddressVerifier.isValidAddrComponent(newVal.getValue())) {
					netAddrErrorLabel.setText("");
				} else {
					netAddrErrorLabel.setText("Error in network address!");
				}
			}

			private void sendNetworkAddressToServer() {
				netAddrErrorLabel.setText("");
				if (!NetworkAddressVerifier.isValidAddrComponent(netAddressFiled1.getValue())
						|| !NetworkAddressVerifier.isValidAddrComponent(netAddressFiled2.getValue())
						|| !NetworkAddressVerifier.isValidAddrComponent(netAddressFiled3.getValue())) {
					netAddrErrorLabel.setText("Error in network address!");
					return;
				}
				scanButton.setEnabled(false);
				canScan = false;
				while (resultTable.getRowCount() > 1) {
					resultTable.removeRow(1);
				}
				String msg = netAddressFiled1.getValue() + "." + netAddressFiled2.getValue() + "."
						+ netAddressFiled3.getValue() + ".";
				// Посылаем в websocket данные (адрес сети, который будем
				// сканировать)
				// Здесь нет проверки на состояние веб сокета!
				socket.send(msg);
				scanStatusLabel.setText("Scaning network " + msg + "0/24...");
				netAddressFiled3.setFocus(true);
				netAddressFiled3.selectAll();
			}

			@Override
			public void onClick(ClickEvent arg0) {
				if (canScan)
					sendNetworkAddressToServer();
			}

			@Override
			public void onKeyUp(KeyUpEvent arg0) {
				if (arg0.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					if (arg0.getSource() == netAddressFiled1) {
						netAddressFiled2.setFocus(true);
						netAddressFiled2.selectAll();
					} else if (arg0.getSource() == netAddressFiled2) {
						netAddressFiled3.setFocus(true);
						netAddressFiled3.selectAll();
					} else if (arg0.getSource() == netAddressFiled3) {
						if (canScan)
							sendNetworkAddressToServer();
					}
				}

			}
		}

		NetworkAddressHandler networkAddressHandler = new NetworkAddressHandler();
		netAddressFiled1.addValueChangeHandler(networkAddressHandler);
		netAddressFiled2.addValueChangeHandler(networkAddressHandler);
		netAddressFiled3.addValueChangeHandler(networkAddressHandler);
		scanButton.addClickHandler(networkAddressHandler);
		netAddressFiled1.addKeyUpHandler(networkAddressHandler);
		netAddressFiled2.addKeyUpHandler(networkAddressHandler);
		netAddressFiled3.addKeyUpHandler(networkAddressHandler);
	}
}
