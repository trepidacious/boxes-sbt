package boxes.transact.fx.demo

import scalafx.application.JFXApp
import scalafx.event.ActionEvent
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.GridPane
import scalafx.scene.paint.Color
import scalafx.geometry.Insets
import boxes.transact.ShelfDefault
import boxes.transact.BoxNow
import boxes.transact.fx.Fox
import boxes.transact.fx.Includes._

object FXDemo extends JFXApp {

  implicit val shelf = ShelfDefault()
  
//  
//  
//  val check = new CheckBox {
//    text = "CheckBox"
//  }
//  
//  check.onAction = (event: ActionEvent) => {
//      lblCheckState.text = if (check.indeterminate.get) "Indeterminate" else check.selected.get().toString
//  }
//
//  val lblCheckState = new Label {
//    text = check.selected.get().toString
//  }
//
//  val btnAllowIndeterminate = new scalafx.scene.control.Button {
//    text = "Allow Indeterminate"
//  }
//  btnAllowIndeterminate.onAction = (event: ActionEvent) => {
//      check.allowIndeterminate = !check.allowIndeterminate.get()
//  }
//
//  val lblAllowIndeterminate = new Label {
//    text <== when(check.allowIndeterminate) choose "Can be Indeterminate" otherwise "Can not be Indeterminate"
//  }
//
//  val btnFire = new Button {
//    text = "Fire!"
//  }
//  btnFire.onAction = (event: ActionEvent) =>  check.fire()

  val s = BoxNow("Hi!")
  
  val text = new TextField
  text.textProperty <==> s

  val text2 = new TextField
  text2.textProperty <==> s

  val label = new Label
  label.textProperty <==> s
  
  val check = new CheckBox {
    text = "CheckBox"
  }

  val b = BoxNow(false)
  check.selectedProperty <==> b

  val bString = BoxNow.calc(implicit txn => if (b()) "selected" else "not selected")
  val bLabel = new Label
  bLabel.textProperty <==> bString
  
  val grid = new GridPane {
    padding = Insets(10)
    hgap = 5
    vgap = 5
  }
  
  grid.add(text, 0, 0)
  grid.add(text2, 0, 1)
  grid.add(label, 0, 2)
  grid.add(check, 0, 3)
  grid.add(bLabel, 0, 4)

  
//  grid.add(check, 0, 0)
//  grid.add(lblCheckState, 1, 0)
//  grid.add(btnAllowIndeterminate, 0, 1)
//  grid.add(lblAllowIndeterminate, 1, 1)
//  grid.add(btnFire, 0, 2)
//  grid.add(txfText, 1, 2)

  stage = new PrimaryStage {
    title = "CheckBox Test"
    scene = new Scene {
      fill = Color.LIGHTGRAY
      content = grid
    }
  }

}