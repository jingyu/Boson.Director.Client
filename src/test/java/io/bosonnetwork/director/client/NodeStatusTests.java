/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.director.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;

/**
 * Tests of finding a service in a node status, which is how clients discover service endpoints.
 */
public class NodeStatusTests {
	@Test
	void servicesAreFoundByServiceId() {
		Id nodeId = Id.random();
		Id first = Id.random();
		Id second = Id.random();
		String json = "{\"nodeId\": \"" + nodeId + "\", \"running\": true, \"startedAt\": 1, \"services\": [" +
				"{\"serviceId\": \"" + NodeStatus.Service.ION_STORE + "\", \"peerId\": \"" + first +
				"\", \"endpoint\": \"https://node.example.com:8090/ion\"}, " +
				"{\"serviceId\": \"" + NodeStatus.Service.ION_STORE + "\", \"peerId\": \"" + second + "\"}, " +
				"{\"serviceId\": \"" + NodeStatus.Service.ACTIVE_PROXY + "\", \"peerId\": \"" + second +
				"\", \"endpoint\": \"tcp://node.example.com:8090\"}]}";

		NodeStatus status = Json.parse(json, NodeStatus.class);

		NodeStatus.Service ionStore = status.getService(NodeStatus.Service.ION_STORE).orElseThrow();
		assertEquals(first, ionStore.getPeerId());
		assertEquals("https://node.example.com:8090/ion", ionStore.getEndpoint().orElseThrow());
		assertTrue(status.getService(NodeStatus.Service.ACTIVE_PROXY).isPresent());
		assertFalse(status.getService(NodeStatus.Service.WEB_GATEWAY).isPresent());
	}
}
