package com.example.starter;

import com.example.starter.chatting.ChattingRepository;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;


public class MainVerticle extends AbstractVerticle {

  static final String STATIC_PATH = System.getProperty("user.dir") + "/src/main/resources";

  public static void main(String[] args){
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    String STATIC_PATH = System.getProperty("user.dir") + "/src/main/resources";
    // vertx-config 라이브러리 설치시 추가되는  resource 경로 데이터파일 접근법  (다수의 설정파일 사용시 활용)
    FileSystem fs = vertx.fileSystem();
    JsonObject temp = new JsonObject();
    JsonObject dbConfig = new JsonObject();
    Buffer buf = fs.readFileBlocking(STATIC_PATH + "/conf/test2.json");
    temp.put("test",Json.decodeValue(buf.toString()));
    Buffer buf2 = fs.readFileBlocking(STATIC_PATH + "/conf/db.json");
    dbConfig.put("db",Json.decodeValue(buf2.toString()));


    ConfigRetriever cr = ConfigRetriever.create(vertx);

    //  ConfigRtriever 의 getConfig 메소드를 통해 vertx 에 걸려있는 모든 설정과 /resources 하단의
    //  /conf/config.json(vertx-config 기본설정) 을 스캔해서 json으로 가져옴.

    cr.getConfig(json -> {
      JsonObject config = json.result().getJsonObject("database");
      System.out.println("config ::11 "+config.toString());
      System.out.println("11 : " + temp.getJsonObject("test"));
      System.out.println("22 : " + dbConfig.getJsonObject("db"));

      // PgConnectOptions 인스턴스 생성시  json 으로 접속정보 설정하면 자동 매핑해서 셋팅함.
    PgConnectOptions connectOptions = new PgConnectOptions(dbConfig.getJsonObject("db"));
    //  PgPool은 interface poll 을,  poll은 SqlClient를 상속받고있음   PgPool로 쓰는게 사용에 유리
    //  poll 형은 sqlClient 의 기능 전부 구현가능
    PgPool pool = PgPool.pool(vertx, connectOptions, new PoolOptions().setMaxSize(10));
    // SqlClient sqlClient = PgPool.pool(vertx, connectOptions, new PoolOptions().setMaxSize(10));

      System.out.println(" 0 ");
      Router router = Router.router(vertx);
      router.route("/");
      router.mountSubRouter("/api",chatRouter(pool));

      router.mountSubRouter("/eventbus",eventbusRouter(pool));

      router.route().handler(StaticHandler.create().setWebRoot("view"));

      vertx.createHttpServer().requestHandler(router).listen(8888);

      // 해당 시간마다  eventbus를 통한 news-feed  address 로  메세지 전송
      JsonObject customMessage = new JsonObject();
      customMessage.put("statusCode",200);
      customMessage.put("resultCode","체크");
      customMessage.put("summary","체크");
      //vertx.setPeriodic(10000,t ->vertx.eventBus().publish("news-feed",customMessage));

    });
  }

  private Router chatRouter(PgPool pool){
    ChattingRepository chattingRepository = new ChattingRepository(pool);

    Router chatRouter = Router.router(vertx);
    chatRouter.route().handler(BodyHandler.create())
      .consumes("application/json")
      .produces("application/json");
    chatRouter.route("/join").handler(chattingRepository::insertChatUser);
    chatRouter.route("/send").handler(chattingRepository::insertMessage);

    return chatRouter;
  }

  private Router eventbusRouter(PgPool pool) {
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    SockJSBridgeOptions options = new SockJSBridgeOptions().addOutboundPermitted(new PermittedOptions());
    return sockJSHandler.bridge(options,event ->{
      if(event.type() == BridgeEventType.SOCKET_CREATED){
        System.out.println("소켓생성");
        System.out.println(event.getRawMessage());
        JsonObject customMessage = new JsonObject();
        customMessage.put("statusCode",200);
        customMessage.put("resultCode","소켓생성");
        customMessage.put("summary","입장하셨습니다.");
        vertx.eventBus().publish("news-feed",customMessage);
      }else if(event.type() == BridgeEventType.PUBLISH ) {
        System.out.println("퍼블리시");
        System.out.println(event.getRawMessage());
      }else if(event.type() == BridgeEventType.RECEIVE ) {
        System.out.println("수신");
        System.out.println(event.getRawMessage());
      }else if(event.type() == BridgeEventType.SEND ) {
        System.out.println("전송");
        System.out.println(event.getRawMessage());
      }else if(event.type() == BridgeEventType.SOCKET_CLOSED ) {
        System.out.println("소켓닫힘");
        System.out.println(event.getRawMessage());
      }
      event.complete(true);
    });
  }

}
