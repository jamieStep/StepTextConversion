package org.stepbible.textconverter.applicationspecificutils

import org.w3c.dom.Node


/******************************************************************************/
/**
 * A supremely pointless class, but for the fact that I have separate OSIS and
 * USX cross-reference checkers, and need something they can inherit from.
 *
 * The derived classes do things like checking references are syntactically
 * correct, point to places which actually exist within the text (eg no OT
 * refs in an NT-only text), and check that the substructure (somewhat
 * complicated in the case of USX) is correct.
 *
 * @author ARA "Jamie" Jamieson
 */

interface CrossReferenceChecker
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Checks cross-references.
  *
  * @param dataCollection Collection to be checked.
  * @param xrefNodes List of cross-reference nodes to be checked.
  */

  fun process (dataCollection: X_DataCollection, xrefNodes: List<Node>)
}