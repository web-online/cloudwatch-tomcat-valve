/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.web.online.cloudwatch.tomcat.valve;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;

/**
 * An adapter that allows code using org.apache.commons.logging.LogFactory to
 * be configured to use the org.apache.juli.logging.LogFactory as it's
 * implementation (via the documented configuration options of
 * org.apache.commons.logging.LogFactory such as commons-logging.properties,
 * JDK 1.3 services interface, etc.)
 * 
 * @author web-online
 */
public class JuliLogFactoryAdapter extends LogFactory {

    /**
     * The class that to which we're adapting
     */
    private final org.apache.juli.logging.LogFactory juliLogFactory = org.apache.juli.logging.LogFactory.getFactory();

    @Override
    public Object getAttribute(String name) {
        return juliLogFactory.getAttribute(name);
    }

    @Override
    public String[] getAttributeNames() {
        return juliLogFactory.getAttributeNames();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Log getInstance(Class clazz) throws LogConfigurationException {
        return new JuliLogAdapter(juliLogFactory.getInstance(clazz));
    }

    @Override
    public Log getInstance(String name) throws LogConfigurationException {
        return new JuliLogAdapter(juliLogFactory.getInstance(name));
    }

    @Override
    public void release() {
        juliLogFactory.release();
    }

    @Override
    public void removeAttribute(String name) {
        juliLogFactory.removeAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        juliLogFactory.setAttribute(name, value);
    }

    /**
     * Adapter class for code using org.apache.commons.logging.Log to
     * to use the org.apache.juli.logging.Log
     */
    static class JuliLogAdapter implements Log {

        /**
         * The class that to which we're adapting
         */
        private org.apache.juli.logging.Log juliLog = null;

        /**
         * Constructor that takes in the JULI Log
         * @param juliLog 
         */
        public JuliLogAdapter(org.apache.juli.logging.Log juliLog) {
            this.juliLog = juliLog;
        }

        @Override
        public boolean isDebugEnabled() {
            return juliLog.isDebugEnabled();
        }

        @Override
        public boolean isErrorEnabled() {
            return juliLog.isErrorEnabled();
        }

        @Override
        public boolean isFatalEnabled() {
            return juliLog.isFatalEnabled();
        }

        @Override
        public boolean isInfoEnabled() {
            return juliLog.isInfoEnabled();
        }

        @Override
        public boolean isTraceEnabled() {
            return juliLog.isTraceEnabled();
        }

        @Override
        public boolean isWarnEnabled() {
            return juliLog.isWarnEnabled();
        }

        @Override
        public void trace(Object message) {
            juliLog.trace(message);
        }

        @Override
        public void trace(Object message, Throwable t) {
            juliLog.trace(message, t);
        }

        @Override
        public void debug(Object message) {
            juliLog.debug(message);
        }

        @Override
        public void debug(Object message, Throwable t) {
            juliLog.debug(message, t);
        }

        @Override
        public void info(Object message) {
            juliLog.info(message);
        }

        @Override
        public void info(Object message, Throwable t) {
            juliLog.info(message, t);
        }

        @Override
        public void warn(Object message) {
            juliLog.warn(message);
        }

        @Override
        public void warn(Object message, Throwable t) {
            juliLog.warn(message, t);
        }

        @Override
        public void error(Object message) {
            juliLog.error(message);
        }

        @Override
        public void error(Object message, Throwable t) {
            juliLog.error(message, t);
        }

        @Override
        public void fatal(Object message) {
            juliLog.fatal(message);
        }

        @Override
        public void fatal(Object message, Throwable t) {
            juliLog.fatal(message, t);
        }

    }
    
}