package com.example.starter.chatting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicReference;

public class ChattingRepository {



  private PgPool pgPool;

  public ChattingRepository(PgPool pgPool) {
    this.pgPool = pgPool;
  }

  public void insertChatUser(RoutingContext context){

    System.out.println(" insertMessage 호출");
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("userId",context.request().getParam("userId"));
    jsonObject.put("message",context.request().getParam("userId") + "님이 입장하셨습니다.");
    pgPool.preparedQuery("INSERT INTO CHAT_USER (user_id) values ($1)")
      .execute(Tuple.of(jsonObject.getString("userId")), ar->{
        if(ar.succeeded()){
          context.response().putHeader("content-type","application/json")
            .setStatusCode(200)
            .end();
          context.vertx().eventBus().publish("news-feed",jsonObject);
          System.out.println("유저저장 성공");
        }else {
          context.response()
            .setStatusCode(422)
            .end("유저저장 실패");
          System.out.println("유저저장 실패");
        }
      });
  }

  public void insertMessage(RoutingContext context){
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("userId",context.request().getParam("userId"));
    jsonObject.put("message",context.request().getParam("message"));
    context.vertx().eventBus().publish("news-feed",jsonObject);
    pgPool
      .preparedQuery("INSERT INTO CHAT (chat_id, user_id, comment, target_id, reg_date, chat_idx) " +
        "VALUES (nextval('seq_chat_idx'), $1, $2, $3, now(),currval('seq_chat_idx'))")
      .execute(
        Tuple.of(jsonObject.getString("userId"), jsonObject.getString("message"), ""), ar ->{
          if(ar.succeeded()){
            System.out.println("메세지 저장 성공");
            context.response().putHeader("content-type","application/json")
              .setStatusCode(200)
              .end();
          }else {
            System.out.println("통신실패");
            context.response().putHeader("content-type","application/json")
              .setStatusCode(423)
              .end("메세지저장 실패");
          }
        });
  }


}
