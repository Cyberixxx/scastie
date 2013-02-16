package com.olegych.scastie

import akka.actor._
import akka.event.LoggingReceive
import com.olegych.scastie.PastesActor._
import com.olegych.scastie.PastesActor.PasteProgress
import com.olegych.scastie.PastesActor.GetPaste
import com.olegych.scastie.PastesActor.AddPaste
import com.olegych.scastie.PastesActor.Paste

/**
  */
class PastesActor(pastesContainer: PastesContainer, progressActor: ActorRef) extends Actor with ActorLogging {
  val renderer = ActorsHome.createRenderer
  val failures = ActorsHome.createFailures

  def receive = LoggingReceive {
    case AddPaste(content, uid) =>
      val id = nextPasteId
      val paste = Paste(id = id, content = Option(content), output = Option("Processing..."), uid = Some(uid))
      renderer ! paste
      sender ! paste
      writePaste(paste)
    case GetPaste(id) =>
      sender ! readPaste(id)
    case DeletePaste(id, uid) =>
      sender ! deletePaste(id, uid)
    case paste: Paste =>
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    val pasteDir = pastesContainer.paste(paste.id)
    val contentChanged = readPaste(paste.id).content != paste.content
    pasteDir.pasteFile.write(paste.content)
    pasteDir.uidFile.write(paste.uid)
    progressActor ! PasteProgress(paste.id, contentChanged, paste.output.getOrElse(""))
    pasteDir.outputFile.write(paste.output, truncate = false)
  }

  def readPaste(id: Long) = {
    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists) {
      Paste(id = id, content = paste.pasteFile.read, output = paste.outputFile.read, uid = paste.uidFile.read)
    } else {
      Paste(id = id, content = None, output = Option("Not found"), uid = None)
    }
  }

  def deletePaste(id: Long, uid: String) = {
    val storedPaste = readPaste(id)
    if (storedPaste.uid == Some(uid)) {
      val paste = pastesContainer.paste(id)
      paste.outputFile.delete()
      paste.pasteFile.delete()
      readPaste(id)
    } else {
      storedPaste
    }
  }

  def nextPasteId = pastesContainer.lastPasteId.incrementAndGet()
}

object PastesActor {

  sealed trait PasteMessage

  case class AddPaste(content: String, uid: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class DeletePaste(id: Long, uid: String) extends PasteMessage

  case class Paste(id: Long, content: Option[String], output: Option[String], uid: Option[String])
      extends PasteMessage

  case class PasteProgress(id: Long, contentChanged: Boolean, output: String)

}
