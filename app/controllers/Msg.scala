package controllers

import play.api.libs.json._

import lila.api.Context
import lila.app._
import lila.common.LightUser.lightUserWrites

final class Msg(
    env: Env
) extends LilaController(env) {

  def home = Auth { implicit ctx => me =>
    negotiate(
      html =
        inboxJson(me) map { json =>
          Ok(views.html.msg.home(json))
        },
      api = v => {
        if (v >= 5) inboxJson(me)
        else env.msg.compat.inbox(me, getInt("page"))
      } map { Ok(_) }
    )
  }

  def threadWith(username: String) = Auth { implicit ctx => me =>
    if (username == "new") Redirect(get("user").fold(routes.Msg.home()) { routes.Msg.threadWith(_) }).fuccess
    else renderConvo(me, username)
  }

  private def renderConvo(me: lila.user.User, username: String)(implicit ctx: Context) =
    env.msg.api.convoWith(me, username) flatMap { convo =>
      def newJson = inboxJson(me).map { _ + ("convo" -> env.msg.json.convo(convo)) }
      negotiate(
        html = newJson map { json =>
          Ok(views.html.msg.home(json))
        },
        api = v => {
          if (v >= 5) newJson
          else fuccess(env.msg.compat.thread(me, convo))
        } map { Ok(_) }
      )
    }

  def search(q: String) = Auth { _ => me =>
    q.trim.some.filter(_.size > 1).filter(lila.user.User.couldBeUsername) match {
      case None    => BadRequest(jsonError("Invalid search query")).fuccess
      case Some(q) => env.msg.search(me, q) flatMap env.msg.json.searchResult(me) map { Ok(_) }
    }
  }

  def unreadCount = Auth { _ => me =>
    JsonOk(env.msg.api unreadCount me)
  }

  def threadDelete(username: String) = Auth { _ => me =>
    env.msg.api.delete(me, username) >>
      inboxJson(me) map { Ok(_) }
  }

  private def inboxJson(me: lila.user.User) =
    env.msg.api.threadsOf(me) flatMap env.msg.json.threads(me) map { threads =>
      Json.obj(
        "me"       -> me.light,
        "contacts" -> threads
      )
    }
}