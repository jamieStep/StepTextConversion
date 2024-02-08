package org.stepbible.textconverter.utils


/******************************************************************************/
/**
 * A supremely pointless class, but for the fact that I have separate OSIS and
 * USX cross-reference checkers, and need something they can inherit from.
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
  fun process (dataCollection: X_DataCollection)
}