package boxes.transact.fx.demo

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.paint.Color
import scalafx.scene.layout.GridPane
import scalafx.geometry.Insets
import scalafx.scene.control.TableView
import javafx.collections.ObservableList
import javafx.collections.FXCollections
import javafx.scene.control.TableColumn
import javafx.util.Callback
import javafx.scene.control.TableCell
import javafx.scene.control.cell.TextFieldTableCell
import javafx.util.StringConverter

object FXTableViewDemo extends JFXApp {

  val l = FXCollections.observableArrayList[Int](1, 2, 3)
  val tv = new TableView[Int](l)
  val col = new TableColumn[Int, Int]("Column")
  
  col.setCellFactory(new Callback[TableColumn[Int, Int], TableCell[Int, Int]]() {
    override def call(p: TableColumn[Int, Int]): TableCell[Int, Int] = {
      new TextFieldTableCell(new StringConverter[Int]() {
          override def toString(t: Int) = t.toString()
          override def fromString(s: String): Int = Integer.parseInt(s)                   
      });
    }
  })
  tv.columns.append(col)
  
//  val c = new Callback
  
//  val grid = new GridPane {
//    padding = Insets(10)
//    hgap = 5
//    vgap = 5
//  }
//  
//  grid.add(tv, 0, 0)
  
  stage = new PrimaryStage {
    title = "CheckBox Test"
    scene = new Scene {
      fill = Color.ANTIQUEWHITE
      content = tv
    }
  }

}