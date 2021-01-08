package com.amazonaws.kafka.samples;

import com.amazonaws.services.kafka.AWSKafkaAsync;
import com.amazonaws.services.kafka.model.ListNodesRequest;
import com.amazonaws.services.kafka.model.ListNodesResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MSK {

    private final AWSKafkaAsync mskClient;
    private static final Logger logger = LogManager.getLogger(MSK.class);

    MSK(AWSKafkaAsync mskClient){
        this.mskClient = mskClient;
    }

    ListNodesResult getNodes() {
        return mskClient.listNodes(new ListNodesRequest().withClusterArn(PrivateLinkCrossAccount.mskClusterArn));
    }

    List<String> getSubnetList(ListNodesResult mskNodes) {
        return mskNodes.getNodeInfoList().stream().map(i -> i.getBrokerNodeInfo().getClientSubnet()).collect(Collectors.toList());
    }

    Map<Double, String> getBrokerIPMap(ListNodesResult mskNodes){
        Map<Double, String> brokerIPMap = new HashMap<>();
        mskNodes.getNodeInfoList().forEach(i -> brokerIPMap.put(i.getBrokerNodeInfo().getBrokerId(), i.getBrokerNodeInfo().getClientVpcIpAddress()));
        return brokerIPMap;
    }

    Map<Integer, String> getBrokerEndpointMap(ListNodesResult mskNodes){
        Map<Integer, String> brokerEndpointMap = new HashMap<>();
        mskNodes.getNodeInfoList().forEach(i -> brokerEndpointMap.put(i.getBrokerNodeInfo().getBrokerId().intValue(), i.getBrokerNodeInfo().getEndpoints().listIterator().next()));
        return brokerEndpointMap;
    }

}
