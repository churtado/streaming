To generate the code for the Customer Specific record, go to the maven menu, then on the
avro examples project click clean and then package. The customer class should be generated there.
That should allow the code to build because the customer class will exist. Basically, it's a maven
plugin that generates type-safe code for building objects. This code is generated by reading the
avro schema file and the config in the pom.xml



# Content

 - Avro examples
 - Kafka Avro Producer & Consumer
 - Schema Evolutions


