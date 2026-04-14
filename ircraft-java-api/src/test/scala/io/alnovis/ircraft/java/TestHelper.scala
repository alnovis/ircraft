package io.alnovis.ircraft.java

import java.util.{ Collections, List => JList }

object TestHelper {

  def jlist[A](xs: A*): JList[A] = {
    val list = new java.util.ArrayList[A]()
    xs.foreach(list.add)
    Collections.unmodifiableList(list)
  }
}
