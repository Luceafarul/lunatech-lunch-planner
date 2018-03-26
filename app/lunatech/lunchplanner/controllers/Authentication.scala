package lunatech.lunchplanner.controllers

import javax.inject.Inject

import com.lunatech.openconnect.Authenticate
import lunatech.lunchplanner.services.UserService
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  BaseController,
  ControllerComponents,
  EssentialAction
}
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Authentication @Inject()(userService: UserService,
                               configuration: Configuration,
                               environment: Environment,
                               auth: Authenticate,
                               val controllerComponents: ControllerComponents)
    extends BaseController {

  def login: EssentialAction = Action { implicit request =>
    if (environment.mode == Mode.Prod) {
      val clientId: String = configuration.get[String]("google.clientId")
      Ok(views.html.login(clientId)).withSession("state" -> auth.generateState)
    } else {
      Redirect(routes.Application.index())
        .withSession("email" -> "developer@lunatech.com")
    }
  }

  def authenticate(code: String,
                   idToken: String,
                   accessToken: String): Action[AnyContent] = Action.async {
    val response = auth.authenticateToken(code, idToken, accessToken)

    response.flatMap {
      case Left(parameters) =>
        userService
          .addUserIfNew(emailAddress = parameters.head._2)
          .map(_ =>
            Redirect(routes.Application.index())
              .withSession(parameters.toArray: _*))
      case Right(message) =>
        Future.successful(
          Redirect(routes.Authentication.login()).withNewSession
            .flashing("error" -> message.toString()))
    }
  }

  def logout: EssentialAction = Action {
    Redirect(routes.Authentication.login()).withNewSession
      .flashing("success" -> "You've been logged out")
  }
}
