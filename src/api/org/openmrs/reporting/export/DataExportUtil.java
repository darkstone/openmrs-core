package org.openmrs.reporting.export;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.MethodExceptionEventHandler;
import org.openmrs.api.context.Context;
import org.openmrs.reporting.PatientSet;
import org.openmrs.util.OpenmrsUtil;

public class DataExportUtil {
	
	private static Log log = LogFactory.getLog(DataExportUtil.class);
	
	/**
	 * 
	 * @param exports
	 */
	public static void generateExports(List<DataExportReportObject> exports) {
		
		for (DataExportReportObject dataExport : exports) {
			try {
				generateExport(dataExport, null);
			}
			catch (Exception e) {
				log.warn("Error while generating export: " + dataExport, e);
			}
		}
		
	}
	
	/**
	 * 
	 * @param dataExport
	 * @param patientSet (nullable)
	 * @throws Exception
	 */
	public static void generateExport(DataExportReportObject dataExport, PatientSet patientSet) throws Exception {
		
		try {
			Velocity.init();
		} catch (Exception e) {
			log.error("Error initializing Velocity engine", e);
		}
		
		File file = getGeneratedFile(dataExport);
		PrintWriter report = new PrintWriter(file);
		
		VelocityContext velocityContext = new VelocityContext();
		
		// Set up velocity utils
		Locale locale = Context.getLocale();
		velocityContext.put("locale", locale);
		
		// Set up functions used in the report ( $!{fn:...} )
		DataExportFunctions functions = new DataExportFunctions();
		velocityContext.put("fn", functions);
		
		// Set up list of patients if one wasn't passed into this method
		if (patientSet == null)
			patientSet = dataExport.generatePatientSet();
		
		// add the error handler
		EventCartridge ec = new EventCartridge();
		ec.addEventHandler(new VelocityExceptionHandler());
		velocityContext.attachEventCartridge(ec);
		
		velocityContext.put("patientSet", patientSet);
		
		String template = dataExport.generateTemplate();
		
		if (log.isDebugEnabled())
			log.debug("Template: " + template.substring(0, template.length() < 3500 ? template.length() : 3500) + "...");
		
		try {
			Velocity.evaluate(velocityContext, report, DataExportUtil.class.getName(), template);
		}
		catch (Exception e) {
			log.error("Error evaluating data export " + dataExport.getReportObjectId(), e);
			log.error("Template: " + template.substring(0, template.length() < 3500 ? template.length() : 3500) + "...");
			report.print("\n\nError: \n" + e.toString() + "\n Stacktrace: \n");
			e.printStackTrace(report);
		}
		finally {
			report.close();
			patientSet = null;
			functions = null;
			velocityContext = null;
			Context.clearSession();
		}
		
	}
	
	/**
	 * Returns the path and name of the generated file
	 * 
	 * @param dataExport
	 * @return
	 */
	public static File getGeneratedFile(DataExportReportObject dataExport) {
		File dir = new File(OpenmrsUtil.getApplicationDataDirectory(), "dataExports");
		dir.mkdirs();
		
		String filename = dataExport.getName().replace(" ", "_");
		filename += "_" + Context.getLocale().toString().toLowerCase();
		
		File file = new File(dir, filename);
		
		return file;
	}
	
	/**
	 * Private class used for velocity error masking
	 */
	public static class VelocityExceptionHandler implements MethodExceptionEventHandler {

		private Log log = LogFactory.getLog(this.getClass());
		
		/**
		 * When a user-supplied method throws an exception, the MethodExceptionEventHandler 
		 * is invoked with the Class, method name and thrown Exception. The handler can 
		 * either return a valid Object to be used as the return value of the method call, 
		 * or throw the passed-in or new Exception, which will be wrapped and propogated to 
		 * the user as a MethodInvocationException
		 * 
		 * @see org.apache.velocity.app.event.MethodExceptionEventHandler#methodException(java.lang.Class, java.lang.String, java.lang.Exception)
		 */
		public Object methodException(Class claz, String method, Exception e) throws Exception {
			
			log.debug("Claz: " + claz.getName() + " method: " + method, e);
			
			// if formatting a date (and probably getting an "IllegalArguementException")
			if ("format".equals(method))
				return null;
			
			// keep the default behaviour
			throw e;
		}

	}
	
}
