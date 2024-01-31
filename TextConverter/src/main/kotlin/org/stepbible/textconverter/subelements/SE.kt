package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.utils.Z_DataCollection
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Common definitions for all processing subelements.
 *
 * @author ARA "Jamie" Jamieson
 */

interface SE
{
  fun doNothing (dataCollection: Z_DataCollection? = null) { }
  fun process (rootNode: Node)
  fun process (dataCollection: Z_DataCollection) = dataCollection.getRootNodes().forEach(::process)
}