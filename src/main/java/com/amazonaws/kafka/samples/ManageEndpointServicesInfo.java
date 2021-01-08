package com.amazonaws.kafka.samples;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.concurrent.ExecutionException;

class ManageEndpointServicesInfo {
    private static AmazonDynamoDBAsync dynamodbClient = Clients.createDynamoDBClient();
    private static DynamoDB dynamoDB = new DynamoDB(dynamodbClient);

    private static final Logger logger = LogManager.getLogger(ManageEndpointServicesInfo.class);

    static void deleteTable(String tableName) {
        Table table = dynamoDB.getTable(tableName);
        try {
            logger.info("Issuing DeleteTable request for {} \n", tableName);
            table.delete();
            logger.info("Waiting for {} to be deleted...this may take a while... \n", tableName);
            table.waitForDelete();
        } catch (Exception e) {
            logger.error("DeleteTable request failed for " + tableName);
            logger.error(Util.stackTrace(e));
        }
    }

    private static ArrayList<AttributeDefinition> getAttributeDefinitions() {
        ArrayList<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        attributeDefinitions
                .add(new AttributeDefinition().withAttributeName("Broker_ID").withAttributeType(ScalarAttributeType.N));
        return attributeDefinitions;
    }

    static void createTable(String tableName, long readCapacityUnits, long writeCapacityUnits) {

        try {

            ArrayList<KeySchemaElement> keySchema = new ArrayList<>();
            keySchema.add(new KeySchemaElement().withAttributeName("Broker_ID").withKeyType(KeyType.HASH)); // Partition Key

            CreateTableRequest request = new CreateTableRequest()
                    .withTableName(tableName)
                    .withKeySchema(keySchema)
                    .withAttributeDefinitions(getAttributeDefinitions())
                    .withProvisionedThroughput(new ProvisionedThroughput()
                            .withReadCapacityUnits(readCapacityUnits)
                            .withWriteCapacityUnits(writeCapacityUnits));


            logger.info("Issuing CreateTable request for {} \n", tableName);
            Table table = dynamoDB.createTable(request);
            logger.info("Waiting for {} to be created...this may take a while... \n", tableName);
            table.waitForActive();

        } catch (Exception e) {
            logger.error("CreateTable request failed for " + tableName);
            logger.error(Util.stackTrace(e));
        }
    }

    static void loadRecord(Integer brokerId, String serviceEndpointDNS, String nlbName, String tableName, String brokerEndpoint) {
        Table table = dynamoDB.getTable(tableName);
        try {
            logger.info("Adding record to table {} \n", tableName);
            Item item = new Item()
                    .withPrimaryKey("Broker_ID", brokerId)
                    .withString("Service_Endpoint_DNS", serviceEndpointDNS)
                    .withString("NLB_Name", nlbName)
                    .withString("Broker_Endpoint", brokerEndpoint);

            table.putItem(item);
        } catch (Exception e) {
            logger.error("Failed to add record in {} \n", tableName);
            logger.error(Util.stackTrace(e));
        }
    }

    static List<String> queryRecords(String tableName, List<String> nlbs) throws ExecutionException, InterruptedException {
        StringJoiner filterExpValues = new StringJoiner(",");
        int valIndex = 1;
        StringBuilder filterExpression = new StringBuilder();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        List<String> endpointServices = new ArrayList<>();

        if (!nlbs.isEmpty()) {
            for (String attributeValue : nlbs) {
                filterExpValues.add(":val" + valIndex);
                expressionAttributeValues.put(":val" + valIndex, new AttributeValue().withS(attributeValue));
                valIndex++;
            }
        }
        filterExpression.append("NLB_Name in (").append(filterExpValues.toString()).append(")");
        ScanRequest scanRequest = new ScanRequest()
                .withFilterExpression(filterExpression.toString())
                .withProjectionExpression("Service_Endpoint_DNS")
                .withExpressionAttributeValues(expressionAttributeValues)
                .withTableName(tableName);
        ScanResult scanResult = dynamodbClient.scanAsync(scanRequest).get();
        scanResult.getItems().forEach(i -> endpointServices.add(i.entrySet().iterator().next().getValue().getS().split("\\.")[4]));
        return endpointServices;
    }
}

