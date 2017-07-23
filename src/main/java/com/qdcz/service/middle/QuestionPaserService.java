package com.qdcz.service.middle;

import com.qdcz.neo4jkernel.LegacyIndexService;
import com.qdcz.neo4jkernel.LoopDataService;
import com.qdcz.sdn.entity._Edge;
import com.qdcz.sdn.entity._Vertex;
import com.qdcz.service.bottom.BankLawService;
import com.qdcz.service.high.TransactionService;
import com.qdcz.tools.BuildReresult;
import com.qdcz.tools.CommonTool;
import com.qdcz.tools.Levenshtein;
import com.qdcz.tools.MyComparetor;
import org.neo4j.graphdb.*;
import org.neo4j.ogm.json.JSONArray;
import org.neo4j.ogm.json.JSONException;
import org.neo4j.ogm.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Created by hadoop on 17-7-13.
 * 对知识库问题的分析获取图谱中的准确信息
 */
@Service
public class QuestionPaserService
{
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private GraphDatabaseService graphDatabaseService;
    @Autowired
    private LegacyIndexService legacyIndexService;
    @Autowired
    private LoopDataService loopDataService;
    @Autowired
    private BankLawService bankLawService;
    public  Map<String, Object> getNode(String question){
        String[] fields={"name"};
        float maxScore = 0;
        Map<String, Object> node =null;
        String type="node";
        List<Map<String, Object>> maps = legacyIndexService.selectByFullTextIndex(fields, question,"vertex");
        MyComparetor mc = new MyComparetor("score");
        Collections.sort(maps,mc);
        Collections.reverse(maps);
        for(Map<String, Object> map:maps){
            float score = (float) map.get("score");//会出错
            if(maxScore<score){
                maxScore = score;
                node = map;
            }else if(maxScore==score){
                Levenshtein lt=new Levenshtein();
                String nodeName = (String)map.get("name");
                String maxName = (String)node.get("name");
                float similarityRatio = lt.getSimilarityRatio(maxName, question);
                float similarityRatio1 = lt.getSimilarityRatio(nodeName, question);
                if(similarityRatio1>similarityRatio){
                    node = map;
                }
            }
        }
        fields= new String[]{"relation"};
        maps = legacyIndexService.selectByFullTextIndex(fields, question,"edge");
        Collections.sort(maps,mc);
        Collections.reverse(maps);
        for(Map<String, Object> map:maps){
            float score = 0;
            try {
                score = Float.parseFloat( map.get("score").toString());//会出错
            }catch (Exception e){
                System.out.println();
            }
            if(maxScore<score){
                type="edge";
                maxScore = score;
                node = map;
            }else if(maxScore==score){
                Levenshtein lt=new Levenshtein();
                String nodeName = (String)map.get("relation");
                String maxName = (String)node.get("relation");
                float similarityRatio = lt.getSimilarityRatio(maxName, question);
                float similarityRatio1 = lt.getSimilarityRatio(nodeName, question);
                if(similarityRatio1>similarityRatio){
                    node = map;
                }
            }
        }
        if(node!=null){
            node.put("type",type);
        }
        return node;
    }
    public Map<String, Object> getCloestMaps(List<Map<String, Object>> maps){
        Map<String, Object> result=null;
        float max=0;
        for(Map<String, Object> node:maps){
            if(node!=null) {
                String name = null;
                float   diffLocation= (float) node.get("score");
                if (diffLocation > max) {
                    max = diffLocation ;
                    result =node;
                }
            }
        }
        return  result;
    }

    public String findDefine(String question,Map<String, Object> map) throws JSONException {
        String[] defineMatchs= new String[]{"是什么","什么是", "定义", "概念", "含义", "指什么","是谁","解释","描述"};
        BuildReresult buildReresult = new BuildReresult();
        boolean flag=false;
        if(map.get("name").equals(question)){
            flag=true;
        }else {
            for (String def : defineMatchs) {
                if (question.contains(def)) {
                    flag = true;
                }
            }
        }
        if(flag){
            StringBuffer sb=new StringBuffer();
            JSONArray resultArray=new JSONArray();
            String name = null;
            if (map.containsKey("relation")) {//边
                name = (String) map.get("relation");
                _Edge edge = bankLawService.checkEdgeById((Long) map.get("id"));
                JSONObject graphById = transactionService.getGraphById(edge.getFrom_id(), 1);
                resultArray.put(graphById);
                JSONObject graphById1 = transactionService.getGraphById(edge.getTo_id(), 1);
                resultArray.put(graphById1);
            }else{//点
                name = (String) map.get("name");
                JSONObject object = transactionService.getGraphById((Long) map.get("id"), 1);
                resultArray.put(object);
            }
            JSONObject merge=new JSONObject();
            //5.组织返回结果
            for(int i=0;i<resultArray.length();i++){
                    merge=buildReresult.mergeResult(merge,resultArray.getJSONObject(i));

            }
            JSONObject result= buildReresult.cleanRestult(merge);
            JSONArray edges = result.getJSONArray("edges");
            for(int i=0;i<edges.length();i++){
                JSONObject edge=edges.getJSONObject(i);
                String relation = edge.getString("relation");
                boolean hasMatchsWord=false;
                for(String def:defineMatchs){
                    if(relation.contains(def)){
                        hasMatchsWord =true;
                        break;
                    }
                }
                if(hasMatchsWord){
                    String from = edge.getString("from");
                    String from_name=null;
                    String to = edge.getString("to");
                    String to_name=null;
                    JSONArray nodes = result.getJSONArray("nodes");
                    for(int m=0;m<nodes.length();m++){
                        JSONObject node=nodes.getJSONObject(m);
                        String id = node.getString("id");
                        if(id.equals(from)){
                            from_name=node.getString("name");
                        }else  if(id.equals(to)){
                            to_name=node.getString("name");
                        }
                        if(from_name!=null&&to_name!=null){
                            break;
                        }
                    }
                    sb.append(from_name+"的"+relation+"为"+to_name+"。");
                }
            }
            return sb.toString();
        }
        return "learning";
    }
    public String traversePathBynode(List<Map<String, Object>> maps){
        if(maps.size()==2){
            Map<String, Object> vertex1=null;
            Map<String, Object> vertex2=null;
            Map<String, Object> edge1=null;
            Map<String, Object> edge2=null;

            for(Map<String, Object> node:maps){
                String  type = (String)node.get("type");
                if("node".equals(type)){
                    if(vertex1!=null){
                        vertex2=node;
                    }else{
                        vertex1=node;
                    }
                }else {
                    if(edge1!=null){
                        edge2=node;
                    }else{
                        edge1=node;
                    }
                }


            }
            String result =null;
            if(vertex2==null&&edge2==null){
                result =  getByNodeAndEdgeName(vertex1,edge1);
            }else if(vertex2==null&&vertex1==null){
                result = getByEdgeAndEdgeName(edge1,edge2);
            }else if(edge2==null&&edge1==null){
                result = getByNodeAndNodeName(vertex1,vertex2);
            }
            return result;
        }
        return "learning";
    }

    private String getByEdgeAndEdgeName(Map<String, Object> edge1,Map<String, Object> edge2){
        String[] fields= new String[]{"relation"};
        String edgeName1=(String)edge1.get("relation");
        String edgeName2=(String)edge2.get("relation");
        List<Map<String, Object>> mapsEdge = legacyIndexService.selectByFullTextIndex(fields, edgeName1,"edge");
        List<Map<String, Object>> mapsEdge2 = legacyIndexService.selectByFullTextIndex(fields, edgeName2,"edge");
        if(mapsEdge.size()==0){
            mapsEdge.add(edge1);
        }
        if(mapsEdge2.size()==0){
            mapsEdge.add(edge2);
        }
        Set<String> resultPaths= new HashSet<>();
        for(Map<String, Object> map:mapsEdge){
            Node nodeStart    =   null;
            try (   Transaction tx = graphDatabaseService.beginTx()) {
                Relationship r = graphDatabaseService.getRelationshipById((Long)map.get("id"));
                tx.acquireReadLock(r);
                nodeStart = r.getStartNode();
                tx.success();
            }
            if(nodeStart!=null) {
                for(Map<String, Object> map2:mapsEdge2){
                    Node nodeEnd    =   null;
                    try (   Transaction tx = graphDatabaseService.beginTx()) {
                        Relationship r = graphDatabaseService.getRelationshipById((Long)map2.get("id"));
                        tx.acquireReadLock(r);
                        nodeEnd = r.getEndNode();
                        tx.success();
                    }
                    Long startid = nodeStart.getId();
                    long endid = nodeEnd.getId();
                    Set<String> strings = loopDataService.loopDataByNodeLevel(startid, endid);
                    resultPaths.addAll(strings);
                }
            }
        }
        Map<String,String> conditions= new HashMap<>();
        conditions.put(edgeName1,"contain");
        conditions.put(edgeName1,"contain");
        StringBuffer sb=new StringBuffer();
        parsePaths(conditions,sb,resultPaths);
        if("".equals(sb.toString())){
            return "learning";
        }else{
            return sb.toString();
        }
    }
    private String getByNodeAndNodeName(Map<String, Object> vertex1 ,Map<String, Object> vertex2){
        String vertexName1=(String)vertex1.get("name");
        String vertexName2=(String)vertex2.get("name");
        List<_Vertex> verticesStart = bankLawService.checkVertexByName(vertexName1);
        List<_Vertex> verticesEnd = bankLawService.checkVertexByName(vertexName2);
        Set<String>  resultPaths= new HashSet<>();
        for(_Vertex vertexeE:verticesEnd){
            for(_Vertex vertexL:verticesStart){
                Long startid = vertexL.getId();
                long endid = vertexeE.getId();
                Set<String> strings = loopDataService.loopDataByNodeLevel(startid, endid);
                resultPaths.addAll(strings);
            }
        }
        Map<String,String> conditions= new HashMap<>();
        StringBuffer sb=new StringBuffer();
        parsePaths(conditions,sb,resultPaths);
        if("".equals(sb.toString())){
            return "learning";
        }else{
            return sb.toString();
        }
    }
    private String getByNodeAndEdgeName(Map<String, Object> vertex,Map<String, Object> edge)  {
        Set<String>  resultPaths= new HashSet<>();
        String vertexName=(String)vertex.get("name");
        List<_Vertex> vertices = bankLawService.checkVertexByName(vertexName);
        //边索引
        String[] fields= new String[]{"relation"};
        String edgeName=(String)edge.get("relation");
        List<Map<String, Object>> mapsEdge = legacyIndexService.selectByFullTextIndex(fields, edgeName,"edge");
        if(mapsEdge.size()==0){//索引找不到的时候直接取id去找
            mapsEdge.add(edge);
        }
        for(Map<String, Object> map:mapsEdge){
            Node node    =   null;
            try (   Transaction tx = graphDatabaseService.beginTx()) {
                Relationship r = graphDatabaseService.getRelationshipById((Long)map.get("id"));
                tx.acquireReadLock(r);
                node = r.getEndNode();
                tx.success();
            }
            if(node!=null) {
                for(_Vertex vertexL:vertices){
                    Long startid = vertexL.getId();
                    long endid = node.getId();
                    Set<String> strings = loopDataService.loopDataByNodeLevel(startid, endid,1);
                    resultPaths.addAll(strings);
                }
            }
        }

        Map<String,String> conditions= new HashMap<>();
        conditions.put(edgeName,"contain");

        StringBuffer sb=new StringBuffer();
        parsePaths(conditions,sb,resultPaths);
        if("".equals(sb.toString())){
            return "learning";
        }else{
            return sb.toString();
        }
    }
    private  StringBuffer parsePaths( Map<String,String> conditions,StringBuffer sb,Set<String>  Paths){
        Set<String> parsePaths =new HashSet<>();
        for(String path:Paths){
            if(path.contains("--")&&!path.contains("<-")) {
                boolean flag = false;
                if(conditions.size()==0){
                    flag =true;
                }else{
                    for (Map.Entry<String, String> entry : conditions.entrySet()){
                        if("contain".equals(entry.getValue().toString())&&path.contains(entry.getKey().toString())){
                            flag =true;
                        }
                    }
                }
                if(flag){
                    String[] split = path.split("--");
                    String result = split[0] + "--" + split[split.length - 1];
                    parsePaths.add(result);
                }
            }
        }
        Map<String,Vector<String>> maps=new HashMap();
        for(String result:parsePaths){
            String[] split = result.split("->");
            String key=split[0];
            String value=split[1];
            if(maps.containsKey(key)){
                Vector<String> strs=maps.get(key);
                strs.add(value);
            }else{
                Vector<String> strs=new Vector<>();
                strs.add(value);
                maps.put(key,strs);
            }
        }
        System.out.println();

        //BaseMongoDAL mongo = new MyMongo(MongoConfigure.dbOnline,"law_details");

        try{
            JSONObject resultJSon=new JSONObject();
            for (Map.Entry<String, Vector<String>> entry : maps.entrySet()){
                JSONArray jsonArray=new JSONArray();
                String result="";
                String key = entry.getKey().replace("--","的");
                resultJSon.put("title",key);
                Vector<String> value = entry.getValue();
                result += key+"为";
                for(String str:value){
                    try{

                        JSONObject object = new JSONObject(str);
                        result="";
                        jsonArray.put(object);
          //              JSONObject oneDocument = mongo.getOneDocument(name);
          //              if(oneDocument!=null) {
          //                  String title = oneDocument.getString("title");
         //                   result += title + "、";
          //              }else{
          //                  result+=name+"、";
          //              }
                    }catch ( JSONException je){
//                        je.printStackTrace();
                        result+=str+"、";
                    }
                }
                if(!"".equals(result)) {
                    result = result.substring(0, result.length() - 1) + "。";
                    sb.append(result);
                }
                else if(jsonArray.length()>0){
                    resultJSon.put("data",jsonArray);
                    sb.append(resultJSon);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
//        finally {
//            if(mongo!=null){
//                mongo.close();
//            }
//        }
        return sb;
    }
    public   String requestTuring(String question) {
        JSONObject request= new JSONObject();
        try {
            request.put("key","149c02a9f63a463f8b55f74b75d2d1c7");
            request.put("info",question);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String respone=null;
        try {
            respone = CommonTool.query(request.toString(), "http://www.tuling123.com/openapi/api");
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("本地没有成功获取 选择调用图灵 \t"+question);
        String result = turingDataParser(respone);
        return result;
    }
    public   String turingDataParser(String str ){
        try {
            JSONObject obj=new JSONObject( str);
            int code = Integer.parseInt(obj.getString("code"));
            if(code==100000){
                return obj.getString("text");
            }else if(code==200000){
                return obj.getString("text")+" "+obj.getString("url");
            }else if(code==302000){
                JSONArray list = obj.getJSONArray("list");
                String result ="";
                result+=obj.getString("text")+"<br>";
                for(int i=0;i<list.length();i++){
                    JSONObject content=list.getJSONObject(i);
                    String s = content.getString("article") + "&nbsp;" + content.getString("source") + "&nbsp;" + content.getString("detailurl")+"<br>";
                    result+= s;
                }
                return result;
            }else if(code==308000){
                JSONArray list = obj.getJSONArray("list");
                String result ="";
                result+=obj.getString("text")+"<br>";
                for(int i=0;i<list.length();i++){
                    JSONObject content=list.getJSONObject(i);
                    String s = content.getString("name") + "&nbsp;" + content.getString("info") + "&nbsp;" + content.getString("detailurl")+"<br>";
                    result+= s;
                }
                return result;
            }else{
                return "还在学习中，请多多关照哦！^-^";
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "还在学习中，请多多关照哦！^-^";
    }
}
