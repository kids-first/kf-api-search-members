<p align="center">
  <img src="search_members.svg" alt="Kids First Search Members" width="660px">
</p>

# kf-api-search-member

This service requires a REST API query request of the form: 
```
/searchmembers?queryString=mytext&start=0&end=50
```
It requires two environment variables :
- elasticsearch.host : Elastic Search cluster Host
- elasticsearch.ports : Elastic Search cluster Port List

## Build

To build the application, run the following from the command line in the root directory of the project

```bash
sbt ";clean;assembly"
```

