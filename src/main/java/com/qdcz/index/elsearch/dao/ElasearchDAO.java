package com.qdcz.index.elsearch.dao;

import com.qdcz.graph.entity.IGraphEntity;
import com.qdcz.index.elsearch.conf.ELKConfig;
import com.qdcz.index.interfaces.IIndexDAO;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by star on 17-8-3.
 */
public class ElasearchDAO implements IIndexDAO {
    private TransportClient client;
    private String index;

    public ElasearchDAO(TransportClient client){
        this.client = client;
        index = ELKConfig.ELKindex;
    }


    @Override
    public void addIndex(IGraphEntity entity) {

    }

    @Override
    public void delIndex(IGraphEntity entity) {
        DeleteResponse response = client.prepareDelete(index, entity.getGraphType(), entity.getGraphId())
                .get();
    }

    @Override
    public void changeIndex(IGraphEntity entity) {

    }

    @Override
    public void bulkIndex(IGraphEntity... entities) {
        if(entities.length == 0){
            return;
        }

        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        for (int i = 0; i < entities.length; i++){
            JSONObject obj=entities[i].toJSON();
            bulkRequestBuilder.add(client.prepareIndex(index, entities[i].getGraphType())
                    .setId(entities[i].getGraphId())
                    .setSource(obj.toString()));

            if(i%20==1 && i!=1){
                BulkResponse bulkResponse = bulkRequestBuilder.get();
                if (bulkResponse.hasFailures()){
                    System.out.println("Bulk add index failures"+bulkResponse.buildFailureMessage());
                }
                bulkRequestBuilder = client.prepareBulk();
            }
        }

        BulkResponse bulkResponse = bulkRequestBuilder.get();
        System.out.println(bulkResponse);
        if (bulkResponse.hasFailures()){
            System.out.println("Bulk add index failures"+bulkResponse.buildFailureMessage());
        }
    }

    @Override
    public void bulkDelete(IGraphEntity... entities) {
        if(entities.length==0){
            return;
        }


        for (IGraphEntity entity:entities){
            DeleteResponse response = client.prepareDelete(index, entity.getGraphType(), entity.getGraphId())
                    .get();

        }
    }





    public List<String> queryAllMatch(IGraphEntity queryEntity) {

//        JSONObject queryJson = queryEntity.toQueryJSON();



        JSONObject queryJson = new JSONObject();
        queryJson.put("root","保险管理");


        QueryBuilder matchQuery = null;


//        matchQuery = QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery()).must(QueryBuilders.termQuery("from", "完善组织架构"));

        matchQuery =  QueryBuilders.queryStringQuery("\"完善组织架构\"").field("from");



//        matchQuery = QueryBuilders.termQuery("from", "完善组织架构");

//        matchQuery = QueryBuilders.matchQuery("root", "保险管理");
//        matchQuery = QueryBuilders.termQuery("root", "保险管理");
//        matchQuery = QueryBuilders.regexpQuery("root", "保险资金");
//        BoolQueryBuilder matchQuery = QueryBuilders.boolQuery();
//        matchQuery.must(QueryBuilders.matchAllQuery());




//        for (Object key :queryJson.keySet()){
//            matchQuery.must(QueryBuilders.matchPhraseQuery(key.toString(), queryJson.getString(key.toString())));
//        }

        // 搜索数据
        SearchResponse response = client.prepareSearch("graph" ).setTypes("edges")
                .setQuery(matchQuery)
                .execute().actionGet();


        int page=1;
        int pagesize=10;
        // 搜索数据(分页)

        //获取查询结果集
        SearchHits searchHits = response.getHits();
        System.out.println(searchHits.totalHits);
        List<String> result= new ArrayList<>();
        //遍历结果
        for(SearchHit hit:searchHits){
            JSONObject source = new JSONObject(hit.getSourceAsString());
            source.put("_id",hit.getId());
            source.put("score",hit.getScore());


            result.add(source.toString());





        }

        System.out.println("共搜到:"+result.size()+"条结果!");
        System.out.println(result);
        return result;
    }

}