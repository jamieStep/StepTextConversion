/******************************************************************************/
package org.stepbible.textconverter.support.stepexception

import kotlin.RuntimeException


/******************************************************************************/
/**
 * A converter-specific exception class.  I've had to use an unchecked
 * exception, to allow for the possibility that I need to raise it from
 * methods which override generic methods (for example, callback methods
 * in XML parsers), where the generic method has not been defined to raise
 * an exception.
 *
 * @author ARA "Jamie" Jamieson
 */

class StepException: RuntimeException
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param e An exception upon which this one is based.
   */

  constructor (e: Exception): super(e.toString())
  {
    stackTrace = e.stackTrace
  }

  
  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param e Another exception -- we take the stack trace from this.
   * 
   * @param overrideReason Augments the description of e. 
   */

  constructor (e: Exception, overrideReason: String): super("$overrideReason: $e")
  {
     stackTrace = e.stackTrace
  }

  
  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param msg Message to appear in exception.
   */

  constructor (msg: String): super(msg)


  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param msg Message to appear in exception.
   * @param wantStackTrace True if we want a stack trace.
   */

  constructor (msg: String, wantStackTrace: Boolean): super(msg)
  {
    setSuppressStackTrace(!wantStackTrace)
  }

  
  /****************************************************************************/
  /**
   * Indicates whether a request has been made to suppress any stack trace.
   * (Used when giving up with a list of errors, for instance, when the stack
   * trace would be meaningless.)
   * 
   * @return True if the stack trace is to be suppressed.
   */
  
  fun getSuppressStackTrace (): Boolean { return m_SuppressStackTrace; }
  
  
  
  /****************************************************************************/
  /**
   * Determines whether the stack trace will be suppressed (by default it is not).
   * 
   * @param v  If true, stack trace will be suppressed.
   */
  
  fun setSuppressStackTrace (v: Boolean) { m_SuppressStackTrace = v; }
  
  
  /****************************************************************************/
  /**
   * Sets the stack trace.
   * 
   * @param stackTrace A stack trace.
   */
  
  @Override fun setStackTrace (stackTrace: Array<StackTraceElement>)
  {
    super.setStackTrace(stackTrace)
  }
  
  
  /****************************************************************************/
  /** 
   * Converts the content to a string.  (Originally I included a stack trace
   * here, but there seems to be an issue with this which causes things to go
   * into a recursive loop.  It will therefore have to be down to processing
   * elsewhere to output a stack trace if required.)
   * 
   * @return String version of exception.
   */
  
  override fun toString (): String
  {
    return (this as Throwable).message as String
  }




  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  private var m_SuppressStackTrace: Boolean = false
}





/******************************************************************************/
/**
* Used to make it possible to break out of processing.
*/

class StepAbandonRun: RuntimeException()

data class StepBreakOutOfProcessing (val reason: String): RuntimeException()

class StepExceptionShouldHaveBeenOverridden (): RuntimeException("Function should have been overridden.")

