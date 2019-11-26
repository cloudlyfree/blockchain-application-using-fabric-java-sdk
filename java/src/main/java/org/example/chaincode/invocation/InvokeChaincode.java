/****************************************************** 
 *  Copyright 2018 IBM Corporation 
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License. 
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0 
 *  Unless required by applicable law or agreed to in writing, software 
 *  distributed under the License is distributed on an "AS IS" BASIS, 
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  See the License for the specific language governing permissions and 
 *  limitations under the License.
 */ 
package org.example.chaincode.invocation;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.example.client.CAClient;
import org.example.client.ChannelClient;
import org.example.client.FabricClient;
import org.example.config.Config;
import org.example.user.SampleUser;
import org.example.user.UserContext;
import org.example.util.Util;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;

/**
 * 
 * @author Balaji Kadambi
 *
 */

public class InvokeChaincode {

	private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
	private static final String EXPECTED_EVENT_NAME = "event";
	private static SampleUser Adminorg1=new SampleUser("peer","Admin","Org1MSP");
	private static SampleUser User1org1=new SampleUser("peer","User1","Org1MSP");
    
	public static void main(String args[]) {
		try {
            Util.cleanUp();
			String caUrl = Config.CA_ORG1_URL;
			File f = new File ("E:\\crypto-config\\peerOrganizations\\traffic.notax.com\\ca\\ca.traffic.notax.com-cert.pem");
            String certficate = new String (IOUtils.toByteArray(new FileInputStream(f)),"UTF-8");
            Properties properties = new Properties();
            properties.put("pemBytes", certficate.getBytes());
            properties.setProperty("pemFile", f.getAbsolutePath());
            properties.setProperty("allowAllHostNames", "true");
			CAClient caClient = new CAClient(caUrl, properties);
			// Enroll Admin to Org1MSP
			UserContext adminUserContext = new UserContext();
			adminUserContext.setName(Config.ADMIN);
			adminUserContext.setAffiliation(Config.ORG1);
			adminUserContext.setMspId(Config.ORG1_MSP);
			caClient.setAdminUserContext(adminUserContext);
			adminUserContext = caClient.enrollAdminUser(Config.ADMIN, Config.ADMIN_PASSWORD);
			
			FabricClient fabClient = new FabricClient(adminUserContext);
			
			ChannelClient channelClient = fabClient.createChannelClient(Config.CHANNEL_NAME);
			Channel channel = channelClient.getChannel();
			
			Properties peer1Prop = new Properties();
	        peer1Prop.setProperty("pemFile", "E:\\crypto-config\\peerOrganizations\\traffic.notax.com\\peers\\peer0.traffic.notax.com\\tls\\ca.crt");
	        peer1Prop.setProperty("sslProvider", "openSSL");
	        peer1Prop.setProperty("negotiationType", "TLS");
	        peer1Prop.setProperty("trustServerCertificate", "true");
	        peer1Prop.setProperty("hostnameOverride", "peer0.traffic.notax.com");
	        peer1Prop.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);
			Peer peer = fabClient.getInstance().newPeer(Config.ORG1_PEER_0, Config.ORG1_PEER_0_URL,peer1Prop);
			EventHub eventHub = fabClient.getInstance().newEventHub("eventhub01", "grpc://localhost:7053");
			
	        Properties orderer1Prop = new Properties();
	        orderer1Prop.setProperty("pemFile", "E:\\crypto-config\\ordererOrganizations\\notax.com\\orderers\\orderer0.notax.com\\tls\\ca.crt");
	        orderer1Prop.setProperty("sslProvider", "openSSL");
	        orderer1Prop.setProperty("negotiationType", "TLS");
	        orderer1Prop.setProperty("hostnameOverride", "orderer0.notax.com");
	        orderer1Prop.setProperty("trustServerCertificate", "true");
			Orderer orderer = fabClient.getInstance().newOrderer(Config.ORDERER_NAME, Config.ORDERER_URL,orderer1Prop);
			channel.addPeer(peer);
			channel.addEventHub(eventHub);
			channel.addOrderer(orderer);
			channel.initialize();

			TransactionProposalRequest request = fabClient.getInstance().newTransactionProposalRequest();
			ChaincodeID ccid = ChaincodeID.newBuilder().setName(Config.CHAINCODE_1_NAME).build();
			request.setChaincodeID(ccid);
			request.setFcn("create");
			
			String uuid = UUID.randomUUID()+"";
			String[] arguments = {uuid};
			request.setArgs(arguments);
			request.setProposalWaitTime(1000);

			Map<String, byte[]> tm2 = new HashMap<>();
			tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); 																								
			tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8)); 
			tm2.put("result", ":)".getBytes(UTF_8));
			tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA); 
			request.setTransientMap(tm2);
			Collection<ProposalResponse> responses = channelClient.sendTransactionProposal(request);
			for (ProposalResponse res: responses) {
				Status status = res.getStatus();
				Logger.getLogger(InvokeChaincode.class.getName()).log(Level.INFO,"Invoked createCar on "+Config.CHAINCODE_1_NAME + ". Status - " + status);
			}
			
			CompletableFuture<TransactionEvent> cf = channel.sendTransaction(responses);
			Thread.sleep(10000);
			
			//peer channel query
			QueryByChaincodeRequest qrequest = fabClient.getInstance().newQueryProposalRequest();
			qrequest.setChaincodeID(ccid);
			qrequest.setFcn("query");
			qrequest.setArgs(arguments);
			qrequest.setProposalWaitTime(1000);
			
			Collection<ProposalResponse> qresponse = channel.queryByChaincode(qrequest);
			for (ProposalResponse pres : qresponse) {
				String stringResponse = new String(pres.getChaincodeActionResponsePayload());
				System.out.println(stringResponse);
			}
									
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
