/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.stepexception

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import kotlin.RuntimeException


/******************************************************************************/
/**
 * Converter-specific exception classes.  I've had to use an unchecked
 * exception, to allow for the possibility that I need to raise it from
 * methods which override generic methods (for example, callback methods
 * in XML parsers), where the generic method has not been defined to raise
 * an exception.
 *
 * [StepExceptionBase] is the base class, whose 'terminate' method includes
 * outputting a stack trace.
 *
 * [StepExceptionWithStackTraceBase] is a derived class functionally identical to
 * [StepExceptionBase].
 *
 * [StepExceptionWithoutStackTraceBase] is a similar derived class, but one which
 * does not output a stack trace.
 *
 * [StepExceptionSilentBase] is a derived class which outputs very little
 * information, and is intended mainly where we have already output any
 * relevant information and do not wish to muddy the picture with additional
 * detail.
 *
 * All of the other classes are simply circumstance-specific derivatives of
 * one of these.  They all have the same behaviour as their parent.  I give
 * different names merely so that we can raise exceptions with names
 * which reflect the reason for raising the exception.
 *
 * In theory, therefore, you should never instantiate one of the base classes
 * directly -- you should work only with derived classes.  Using the base
 * classes will *work*, but the resulting code would be less meaningful.
 *
 * @author ARA "Jamie" Jamieson
 */

open class StepExceptionBase: RuntimeException
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
   */

  constructor (): super()
  {
  }

  
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
  * Full 'bad news' termination processing, complete with stack trace etc.
  */
  
  open fun terminate ()
  {
    Dbg.endOfRun()
    if (null != message) println(message)
    printStackTrace()
    System.err.println("Fatal error: " + Dbg.getActiveProcessingId() + ": " + this.toString())
    System.err.flush()
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
}


/******************************************************************************/
/**
* A [StepExceptionBase] which keeps relatively stumm on exiting the process.
*/

open class StepExceptionSilentBase: StepExceptionBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)



  /****************************************************************************/
  /**
  * Nothing much by way of messaging at the end of the run.
  */

  override fun terminate ()
  {
    Dbg.endOfRun()
  }
}


/******************************************************************************/
/**
* This is just a synonym for [StepExceptionBase].
*/

open class StepExceptionWithStackTraceBase: StepExceptionBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}


/******************************************************************************/
/**
* This is a [StepExceptionBase] which does not output a stack trace in its
* termination processing.
*/

open class StepExceptionWithoutStackTraceBase: StepExceptionBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}







/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                             Derived classes                              **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
class StepExceptionWithStackTraceShouldHaveBeenOverridden: StepExceptionWithStackTraceBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}


/******************************************************************************/
class StepExceptionWithStackTraceAbandonRun: StepExceptionWithStackTraceBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}


/******************************************************************************/
class StepExceptionWithoutStackTraceAbandonRun: StepExceptionWithoutStackTraceBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}


/******************************************************************************/
class StepExceptionSilentCommandLineIssue: StepExceptionBase
{
  constructor (): super()
  constructor (reason: String): super(reason)
  constructor (e: Exception): super(e)
}


/******************************************************************************/
class StepExceptionSilentAbandonRunBecauseErrorsRecordedInLog: StepExceptionBase
{
  constructor (): super()
  constructor (reason: String): super(reason)
  constructor (e: Exception): super(e)
}