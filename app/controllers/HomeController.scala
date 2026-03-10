package controllers

import play.api.mvc.AbstractController
import play.api.mvc.ControllerComponents

class HomeController(cc: ControllerComponents) extends AbstractController(cc) {
  def index() = Action { Ok(views.html.index()) }
}
