ElasticSearch 101

ElasticSearch doesn't have databases, it has indexes

Indexes don't have tables, they have index types

ElasticSearch exposes a rest API to to CRUD stuff on indexes and index types. Below are examples:

server health:      GET     /_cat/health?v
nodes:              GET     /_cat/nodes?v
indices:            GET     /_cat/indices?v
create index:       PUT     /twitter             creates twitter index


add a document:     PUT     /twitter/_doc/1?pretty   will add a document. Example document:
{
   "name": "John Doe"
}

The document doesn't require any structure. You could go further by using an index type:
PUT     /twitter/tweets/1
{
   "name": "John Doe"
}

Now you have an index type of tweet in your twitter index

delete document:    DELETE      /twitter/tweets/1