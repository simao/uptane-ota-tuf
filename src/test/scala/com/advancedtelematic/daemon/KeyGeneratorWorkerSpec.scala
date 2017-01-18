package com.advancedtelematic.daemon

import akka.actor.{ActorSystem, Status}
import akka.testkit.{ImplicitSender, TestKitBase}
import com.advancedtelematic.ota_tuf.daemon.KeyGeneratorWorker
import com.advancedtelematic.ota_tuf.data.DataType.{GroupId, Key, KeyGenId, KeyGenRequest}
import com.advancedtelematic.ota_tuf.data.{KeyGenRequestStatus, RoleType}
import com.advancedtelematic.ota_tuf.db.{KeyGenRequestSupport, KeyRepositorySupport}
import com.advancedtelematic.util.OtaTufSpec
import org.genivi.sota.core.DatabaseSpec
import org.genivi.sota.http.Errors.MissingEntity
import com.advancedtelematic.ota_tuf.crypt.RsaKeyPair.keyShow
import cats.syntax.show.toShowOps

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class KeyGeneratorWorkerSpec extends OtaTufSpec with TestKitBase with DatabaseSpec with ImplicitSender
  with KeyRepositorySupport
  with KeyGenRequestSupport {
  override implicit lazy val system: ActorSystem = ActorSystem("KeyGeneratorWorkerIntegrationSpec")

  implicit val ec = ExecutionContext.global

  val actorRef = system.actorOf(KeyGeneratorWorker.props(fakeVault))

  def keyGenRequest: Future[KeyGenRequest] = {
    val keyGenId = KeyGenId.generate()
    val groupId = GroupId.generate()
    keyGenRepo.persist(KeyGenRequest(keyGenId, groupId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT))
  }

  test("generates a key for a key gen request") {
    val keyGenReq = keyGenRequest.futureValue
    actorRef ! keyGenReq

    val key = expectMsgPF() {
      case Status.Success(t: Key) => t
    }

    keyGenRepo.find(keyGenReq.id).futureValue.status shouldBe KeyGenRequestStatus.GENERATED

    keyRepo.find(key.id).futureValue.publicKey.show should include("BEGIN PUBLIC KEY")
  }

  test("associates new key with role") {
    val keyGenReq = keyGenRequest.futureValue
    actorRef ! keyGenReq

    val key = expectMsgPF() {
      case Status.Success(t: Key) => t
    }

    val keys = keyRepo.keysFor(keyGenReq.groupId).futureValue

    keys.map(_.id) should contain(key.id)
  }

  test("sends back Failure if something bad happens") {
    val groupId = GroupId.generate()
    actorRef ! KeyGenRequest(KeyGenId.generate(), groupId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT)
    val exception = expectMsgType[Status.Failure](3.seconds)
    exception.cause shouldBe a[MissingEntity]
  }

  test("keys with an error are marked as error") {
    val keyGenId = KeyGenId.generate()
    val groupId = GroupId.generate()
    val kgr = keyGenRepo.persist(KeyGenRequest(keyGenId, groupId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keySize = -1)).futureValue
    actorRef ! kgr

    val exception = expectMsgType[Status.Failure](3.seconds)
    exception.cause shouldBe a[IllegalArgumentException]

    keyGenRepo.find(keyGenId).futureValue.status shouldBe KeyGenRequestStatus.ERROR
  }

  test("adds key to vault") {
    actorRef ! keyGenRequest.futureValue

    val key = expectMsgPF() {
      case Status.Success(t: Key) => t
      case Status.Failure(ex) => fail(ex)
    }

    fakeVault.findKey(key.id).futureValue.publicKey should include("BEGIN PUBLIC KEY")
    fakeVault.findKey(key.id).futureValue.privateKey should include("BEGIN RSA PRIVATE KEY")
  }

  test("threshold keys per role") (pending)
}
