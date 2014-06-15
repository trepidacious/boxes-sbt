package boxes.transact.swing.layout

import java.awt.{CardLayout, BorderLayout, Dimension, Container, Component, LayoutManager}
import javax.swing.{JPanel, Icon, JComponent}
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.BoxNow
import boxes.transact.swing.views.BooleanView
import boxes.transact.swing.views.Tab
import boxes.swing.SwingView
import boxes.swing.TabSpacer
import boxes.transact.reaction.RadioReaction

object VerticalTabLayout {
  def apply(tabWidth:Int = 64, tabHeight:Int = 64) = new VerticalTabLayout(tabWidth, tabHeight)
}

class VerticalTabLayout(val tabWidth:Int, val tabHeight:Int) extends LayoutManager {

  override def addLayoutComponent(name:String, c:Component) {}

  override def removeLayoutComponent(c:Component) {}

  override def preferredLayoutSize(parent:Container) = {
    new Dimension(tabWidth, tabHeight * parent.getComponentCount)
  }

  override def minimumLayoutSize(parent:Container) = preferredLayoutSize(parent)

  override def layoutContainer(parent:Container) {
    parent.getComponents.zipWithIndex.foreach{case(c, i) => c.setBounds(0, i * tabHeight, parent.getWidth, tabHeight)}
  }

}

case class TabBuilder(toggles: List[Box[Boolean]] = List(), tabComponents: List[JComponent] = List(), contentComponents: List[JComponent] = List())(implicit shelf: Shelf) {

  def add(contents:JComponent, name:Box[String] = BoxNow(""), icon: Box[Option[Icon]] = BoxNow(None), v: Box[Boolean] = BoxNow(toggles.isEmpty)): TabBuilder = {
    val view = BooleanView.extended(v, name, Tab, icon, false)
    TabBuilder(toggles:::List(v), tabComponents:::List(view.component), contentComponents:::List(contents))
  }

  def addView(contents: SwingView, name: Box[String] = BoxNow(""), icon: Box[Option[Icon]] = BoxNow(None), v: Box[Boolean] = BoxNow(toggles.isEmpty)): TabBuilder = add(contents.component, name, icon, v)

  def panel(width:Int = 64, height:Int = 64) = {
    RadioReaction.now(toggles:_*)
    val tabPanel = new JPanel(VerticalTabLayout(width, height))
    tabComponents.foreach(c => tabPanel.add(c))

    val cardLayout = new CardLayout()
    val contentPanel = new JPanel(cardLayout)
    //Blank panel for when nothing is selected
    contentPanel.add(new JPanel(), "-1")
    contentComponents.zipWithIndex.foreach{case(c, i) => contentPanel.add(c, i.toString)}

    //Show the selected content panel card
    val showCardView = shelf.view(implicit txn => {
      val index = toggles.indexWhere(_())
      SwingView.replaceUpdate(this, cardLayout.show(contentPanel, index.toString))
    })

    val sidePanel = new JPanel(new BorderLayout())
    sidePanel.add(tabPanel, BorderLayout.NORTH)
    sidePanel.add(TabSpacer(), BorderLayout.CENTER)

    //View would be lost if not retained by panel
    val panel = new LinkingJPanel(showCardView, new BorderLayout())
    panel.add(sidePanel, BorderLayout.WEST)
    panel.add(contentPanel, BorderLayout.CENTER)
    panel
  }

}

class LinkingJPanel(val view: AnyRef, layout: LayoutManager) extends JPanel(layout)

