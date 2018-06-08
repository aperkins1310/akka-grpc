//#full-client
package example.myapp.helloworld;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.grpc.StatusRuntimeException;
import io.grpc.internal.testing.TestUtils;

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.grpc.GrpcClientSettings;

import example.myapp.helloworld.grpc.*;

class GreeterClient {
  public static void main(String[] args) throws Exception {

    String serverHost = "127.0.0.1";
    int serverPort = 8080;

    GrpcClientSettings settings = GrpcClientSettings.create(serverHost, serverPort)
      // Note: In this sample we are using a dummy TLS cert so we need to fake the authority
      .withOverrideAuthority("foo.test.google.fr")
      .withTrustedCaCertificate("rootCA.crt");

    ActorSystem system = ActorSystem.create();
    Materializer materializer = ActorMaterializer.create(system);

    GreeterServiceClient client = null;
    try {
      client = GreeterServiceClient.create(settings, materializer, system.dispatcher());

      singleRequestReply(client);
      streamingRequest(client);
      streamingReply(client, materializer);
      streamingRequestReply(client, materializer);


    } catch (StatusRuntimeException e) {
      System.out.println("Status: " + e.getStatus());
    } catch (Exception e)  {
      System.out.println(e);
    } finally {
      if (client != null) client.close();
      system.terminate();
    }

  }

  private static void singleRequestReply(GreeterService client) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    CompletionStage<HelloReply> reply = client.sayHello(request);
    System.out.println("got single reply: " + reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingRequest(GreeterService client) throws Exception {
    List<HelloRequest> requests = Arrays.asList("Alice", "Bob", "Peter")
        .stream().map(name -> HelloRequest.newBuilder().setName(name).build())
        .collect(Collectors.toList());
    CompletionStage<HelloReply> reply = client.itKeepsTalking(Source.from(requests));
    System.out.println("got single reply for streaming requests: " +
        reply.toCompletableFuture().get(5, TimeUnit.SECONDS));
  }

  private static void streamingReply(GreeterService client, Materializer mat) throws Exception {
    HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
    Source<HelloReply, NotUsed> responseStream = client.itKeepsReplying(request);
    CompletionStage<Done> done =
      responseStream.runForeach(reply ->
        System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

  private static void streamingRequestReply(GreeterService client, Materializer mat) throws Exception {
    FiniteDuration interval = Duration.create(100, TimeUnit.SECONDS);
    Source<HelloRequest, NotUsed> requestStream = Source
      .tick(interval, interval, "tick")
      .zipWithIndex()
      .map(pair -> pair.second())
      .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
      .take(10)
      .mapMaterializedValue(m -> NotUsed.getInstance());

    Source<HelloReply, NotUsed> responseStream = client.streamHellos(requestStream);
    CompletionStage<Done> done =
      responseStream.runForeach(reply ->
        System.out.println("got streaming reply: " + reply.getMessage()), mat);

    done.toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

}
//#full-client
