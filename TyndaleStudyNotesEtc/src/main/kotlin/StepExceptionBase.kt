/******************************************************************************/
import kotlin.RuntimeException
import kotlin.system.exitProcess


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
   *
   * @param wantStackTrace True if we want a stack trace when baling out.
   */

  constructor (wantStackTrace: Boolean = true): super()
  {
    m_WantStackTrace = wantStackTrace
  }

  
  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param e An exception upon which this one is based.
   * @param wantStackTrace True if we want a stack trace when baling out.
   */

  constructor (e: Exception, wantStackTrace: Boolean = true): super(e.toString())
  {
    m_WantStackTrace = wantStackTrace
    stackTrace = e.stackTrace
  }


  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param e Another exception -- we take the stack trace from this.
   * @param overrideReason Augments the description of e.
   * @param wantStackTrace True if we want a stack trace when baling out.
   */

  constructor (e: Exception, overrideReason: String, wantStackTrace: Boolean = true): super("$overrideReason: $e")
  {
    m_WantStackTrace = wantStackTrace
    stackTrace = e.stackTrace
  }

  
  /****************************************************************************/
  /**
   * Constructor.
   *
   * @param msg Message to appear in exception.
   * @param wantStackTrace True if we want a stack trace when baling out.
   */

  constructor (msg: String, wantStackTrace: Boolean = true): super(msg)
  {
    m_WantStackTrace = wantStackTrace
  }


  /****************************************************************************/
  /**
  * Full 'bad news' termination processing, complete with stack trace etc.
  */
  
  open fun terminate ()
  {
    if (null != message) println(message)
    if (m_WantStackTrace) printStackTrace()
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


  /****************************************************************************/
  private var m_WantStackTrace = true
}


/******************************************************************************/
/**
* A [StepExceptionBase] which keeps relatively stumm on exiting the process.
*/

open class StepExceptionSilentBase: StepExceptionBase
{
  constructor(): super(wantStackTrace = false)
  constructor (e: Exception): super(e, wantStackTrace = false)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason, wantStackTrace = false)
  constructor (msg: String): super(msg, wantStackTrace = false)



  /****************************************************************************/
  /**
  * Nothing much by way of messaging at the end of the run.
  */

  override fun terminate ()
  {
    exitProcess(99)
  }
}


/******************************************************************************/
/**
* This is just a synonym for [StepExceptionBase].
*/

open class StepExceptionWithStackTraceBase: StepExceptionBase
{
  constructor(): super(wantStackTrace = true)
  constructor (e: Exception): super(e, wantStackTrace = true)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason, wantStackTrace = true)
  constructor (msg: String): super(msg, wantStackTrace = true)
}


/******************************************************************************/
/**
* This is a [StepExceptionBase] which does not output a stack trace in its
* termination processing.
*/

open class StepExceptionWithoutStackTraceBase: StepExceptionBase
{
  constructor(): super(wantStackTrace = false)
  constructor (e: Exception): super(e, wantStackTrace = false)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason, wantStackTrace = false)
  constructor (msg: String): super(msg, wantStackTrace = false)
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                             Derived classes                              **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
class StepExceptionNotReallyAnException: StepExceptionWithStackTraceBase
{
  constructor()
  constructor (e: Exception): super(e)
  constructor (e: Exception, overrideReason: String): super(e, overrideReason)
  constructor (msg: String): super(msg)
}


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
