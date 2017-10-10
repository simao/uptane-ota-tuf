package com.advancedtelematic.tuf.reposerver.data

import java.time.Instant

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.ClientDataType.{MetaItem, MetaPath, _}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, SignedPayload, TargetFilename}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import io.circe.{Encoder, Json}
import io.circe.syntax._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest

object RepositoryDataType {
  object StorageMethod extends Enumeration {
    type StorageMethod = Value
    val Managed, Unmanaged = Value
  }

  import StorageMethod._

  case class TargetItem(repoId: RepoId, filename: TargetFilename, uri: Uri, checksum: Checksum, length: Long, custom: Option[TargetCustom] = None, storageMethod: StorageMethod = Managed)

  case class SignedRole(repoId: RepoId, roleType: RoleType, content: SignedPayload[Json], checksum: Checksum, length: Long, version: Int, expireAt: Instant)

  implicit class SignedRoleMetaItemOps(signedRole: SignedRole) {
    def asMetaRole: (MetaPath, MetaItem) = {
      val hashes = Map(signedRole.checksum.method -> signedRole.checksum.hash)
      signedRole.roleType.toMetaPath -> MetaItem(hashes, signedRole.length, signedRole.version)
    }
  }

  object SignedRole {
    def withChecksum[T : Encoder](repoId: RepoId, roleType: RoleType, content: SignedPayload[T], version: Int, expireAt: Instant): SignedRole = {
      val canonicalJson = content.asJson.canonical
      val checksum = Sha256Digest.digest(canonicalJson.getBytes)
      SignedRole(repoId, roleType, SignedPayload(content.signatures, content.signed.asJson), checksum, canonicalJson.length, version, expireAt: Instant)
    }
  }
}
