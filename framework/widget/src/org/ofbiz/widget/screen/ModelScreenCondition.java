/*
 * Copyright 2001-2006 The Apache Software Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.ofbiz.widget.screen;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.collections.FlexibleMapAccessor;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entityext.permission.EntityPermissionChecker;
import org.ofbiz.minilang.operation.BaseCompare;
import org.ofbiz.security.Security;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.w3c.dom.Element;

/**
 * Widget Library - Screen model condition class
 *
 * @author     <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @since      3.1
 */
public class ModelScreenCondition implements Serializable {
    public static final String module = ModelScreenCondition.class.getName();

    protected ModelScreen modelScreen;
    protected ScreenCondition rootCondition;

    public ModelScreenCondition(ModelScreen modelScreen, Element conditionElement) {
        this.modelScreen = modelScreen;
        Element firstChildElement = UtilXml.firstChildElement(conditionElement);
        this.rootCondition = readCondition(modelScreen, firstChildElement);
    }

    public boolean eval(Map context) {
        if (rootCondition == null) {
            return true;
        }
        return rootCondition.eval(context);
    }
    
    public static abstract class ScreenCondition implements Serializable {
        protected ModelScreen modelScreen;

        public ScreenCondition(ModelScreen modelScreen, Element conditionElement) {
            this.modelScreen = modelScreen;
        }
        
        public abstract boolean eval(Map context);
    }
    
    public static List readSubConditions(ModelScreen modelScreen, Element conditionElement) {
        List condList = new LinkedList();
        List subElementList = UtilXml.childElementList(conditionElement);
        Iterator subElementIter = subElementList.iterator();
        while (subElementIter.hasNext()) {
            Element subElement = (Element) subElementIter.next();
            condList.add(readCondition(modelScreen, subElement));
        }
        return condList;
    }
    
    public static ScreenCondition readCondition(ModelScreen modelScreen, Element conditionElement) {
        if (conditionElement == null) {
            return null;
        }
        if ("and".equals(conditionElement.getNodeName())) {
            return new And(modelScreen, conditionElement);
        } else if ("xor".equals(conditionElement.getNodeName())) {
            return new Xor(modelScreen, conditionElement);
        } else if ("or".equals(conditionElement.getNodeName())) {
            return new Or(modelScreen, conditionElement);
        } else if ("not".equals(conditionElement.getNodeName())) {
            return new Not(modelScreen, conditionElement);
        } else if ("if-has-permission".equals(conditionElement.getNodeName())) {
            return new IfHasPermission(modelScreen, conditionElement);
        } else if ("if-validate-method".equals(conditionElement.getNodeName())) {
            return new IfValidateMethod(modelScreen, conditionElement);
        } else if ("if-compare".equals(conditionElement.getNodeName())) {
            return new IfCompare(modelScreen, conditionElement);
        } else if ("if-compare-field".equals(conditionElement.getNodeName())) {
            return new IfCompareField(modelScreen, conditionElement);
        } else if ("if-regexp".equals(conditionElement.getNodeName())) {
            return new IfRegexp(modelScreen, conditionElement);
        } else if ("if-empty".equals(conditionElement.getNodeName())) {
            return new IfEmpty(modelScreen, conditionElement);
        } else if ("if-entity-permission".equals(conditionElement.getNodeName())) {
            return new IfEntityPermission(modelScreen, conditionElement);
        } else {
            throw new IllegalArgumentException("Condition element not supported with name: " + conditionElement.getNodeName());
        }
    }
    
    public static class And extends ScreenCondition {
        protected List subConditions;
        
        public And(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.subConditions = readSubConditions(modelScreen, condElement);
        }
        
        public boolean eval(Map context) {
            // return false for the first one in the list that is false, basic and algo
            Iterator subConditionIter = this.subConditions.iterator();
            while (subConditionIter.hasNext()) {
                ScreenCondition subCondition = (ScreenCondition) subConditionIter.next();
                if (!subCondition.eval(context)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    public static class Xor extends ScreenCondition {
        protected List subConditions;
        
        public Xor(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.subConditions = readSubConditions(modelScreen, condElement);
        }
        
        public boolean eval(Map context) {
            // if more than one is true stop immediately and return false; if all are false return false; if only one is true return true
            boolean foundOneTrue = false;
            Iterator subConditionIter = this.subConditions.iterator();
            while (subConditionIter.hasNext()) {
                ScreenCondition subCondition = (ScreenCondition) subConditionIter.next();
                if (subCondition.eval(context)) {
                    if (foundOneTrue) {
                        // now found two true, so return false
                        return false;
                    } else {
                        foundOneTrue = true;
                    }
                }
            }
            return foundOneTrue;
        }
    }
    
    public static class Or extends ScreenCondition {
        protected List subConditions;
        
        public Or(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.subConditions = readSubConditions(modelScreen, condElement);
        }
        
        public boolean eval(Map context) {
            // return true for the first one in the list that is true, basic or algo
            Iterator subConditionIter = this.subConditions.iterator();
            while (subConditionIter.hasNext()) {
                ScreenCondition subCondition = (ScreenCondition) subConditionIter.next();
                if (subCondition.eval(context)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    public static class Not extends ScreenCondition {
        protected ScreenCondition subCondition;
        
        public Not(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            Element firstChildElement = UtilXml.firstChildElement(condElement);
            this.subCondition = readCondition(modelScreen, firstChildElement);
        }
        
        public boolean eval(Map context) {
            return !this.subCondition.eval(context);
        }
    }
    
    public static class IfHasPermission extends ScreenCondition {
        protected FlexibleStringExpander permissionExdr;
        protected FlexibleStringExpander actionExdr;
        
        public IfHasPermission(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.permissionExdr = new FlexibleStringExpander(condElement.getAttribute("permission"));
            this.actionExdr = new FlexibleStringExpander(condElement.getAttribute("action"));
        }
        
        public boolean eval(Map context) {
            // if no user is logged in, treat as if the user does not have permission
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            if (userLogin != null) {
                String permission = permissionExdr.expandString(context);
                String action = actionExdr.expandString(context);
                
                Security security = (Security) context.get("security");
                if (action != null && action.length() > 0) {
                    // run hasEntityPermission
                    if (security.hasEntityPermission(permission, action, userLogin)) {
                        return true;
                    }
                } else {
                    // run hasPermission
                    if (security.hasPermission(permission, userLogin)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class IfValidateMethod extends ScreenCondition {
        protected FlexibleMapAccessor fieldAcsr;
        protected FlexibleStringExpander methodExdr;
        protected FlexibleStringExpander classExdr;
        
        public IfValidateMethod(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.fieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("field-name"));
            this.methodExdr = new FlexibleStringExpander(condElement.getAttribute("method"));
            this.classExdr = new FlexibleStringExpander(condElement.getAttribute("class"));
        }
        
        public boolean eval(Map context) {
            String methodName = this.methodExdr.expandString(context);
            String className = this.classExdr.expandString(context);
            
            Object fieldVal = this.fieldAcsr.get(context);
            String fieldString = null;
            if (fieldVal != null) {
                try {
                    fieldString = (String) ObjectType.simpleTypeConvert(fieldVal, "String", null, null);
                } catch (GeneralException e) {
                    Debug.logError(e, "Could not convert object to String, using empty String", module);
                }
            }

            // always use an empty string by default
            if (fieldString == null) fieldString = "";

            Class[] paramTypes = new Class[] {String.class};
            Object[] params = new Object[] {fieldString};

            Class valClass;
            try {
                valClass = ObjectType.loadClass(className);
            } catch (ClassNotFoundException cnfe) {
                Debug.logError("Could not find validation class: " + className, module);
                return false;
            }

            Method valMethod;
            try {
                valMethod = valClass.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException cnfe) {
                Debug.logError("Could not find validation method: " + methodName + " of class " + className, module);
                return false;
            }

            Boolean resultBool = Boolean.FALSE;
            try {
                resultBool = (Boolean) valMethod.invoke(null, params);
            } catch (Exception e) {
                Debug.logError(e, "Error in IfValidationMethod " + methodName + " of class " + className + ", defaulting to false ", module);
            }

            return resultBool.booleanValue();
        }
    }
    
    public static class IfCompare extends ScreenCondition {
        protected FlexibleMapAccessor fieldAcsr;
        protected FlexibleStringExpander valueExdr;

        protected String operator;
        protected String type;
        protected FlexibleStringExpander formatExdr;
        
        public IfCompare(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.fieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("field-name"));
            this.valueExdr = new FlexibleStringExpander(condElement.getAttribute("value"));
            
            this.operator = condElement.getAttribute("operator");
            this.type = condElement.getAttribute("type");

            this.formatExdr = new FlexibleStringExpander(condElement.getAttribute("format"));
        }
        
        public boolean eval(Map context) {
            String value = this.valueExdr.expandString(context);
            String format = this.formatExdr.expandString(context);
            
            Object fieldVal = this.fieldAcsr.get(context);
            
            // always use an empty string by default
            if (fieldVal == null) {
                fieldVal = "";
            }

            List messages = new LinkedList();
            Boolean resultBool = BaseCompare.doRealCompare(fieldVal, value, operator, type, format, messages, null, null);
            if (messages.size() > 0) {
                messages.add(0, "Error with comparison in if-compare between field [" + fieldAcsr.toString() + "] with value [" + fieldVal + "] and value [" + value + "] with operator [" + operator + "] and type [" + type + "]: ");

                StringBuffer fullString = new StringBuffer();
                Iterator miter = messages.iterator();
                while (miter.hasNext()) {
                    fullString.append((String) miter.next());
                }
                Debug.logWarning(fullString.toString(), module);

                throw new IllegalArgumentException(fullString.toString());
            }
            
            return resultBool.booleanValue();
        }
    }
    
    public static class IfCompareField extends ScreenCondition {
        protected FlexibleMapAccessor fieldAcsr;
        protected FlexibleMapAccessor toFieldAcsr;

        protected String operator;
        protected String type;
        protected FlexibleStringExpander formatExdr;
        
        public IfCompareField(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.fieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("field-name"));
            this.toFieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("to-field-name"));
            
            this.operator = condElement.getAttribute("operator");
            this.type = condElement.getAttribute("type");

            this.formatExdr = new FlexibleStringExpander(condElement.getAttribute("format"));
        }
        
        public boolean eval(Map context) {
            String format = this.formatExdr.expandString(context);
            
            Object fieldVal = this.fieldAcsr.get(context);
            Object toFieldVal = this.toFieldAcsr.get(context);
            
            // always use an empty string by default
            if (fieldVal == null) {
                fieldVal = "";
            }

            List messages = new LinkedList();
            Boolean resultBool = BaseCompare.doRealCompare(fieldVal, toFieldVal, operator, type, format, messages, null, null);
            if (messages.size() > 0) {
                messages.add(0, "Error with comparison in if-compare-field between field [" + fieldAcsr.toString() + "] with value [" + fieldVal + "] and to-field [" + toFieldAcsr.toString() + "] with value [" + toFieldVal + "] with operator [" + operator + "] and type [" + type + "]: ");

                StringBuffer fullString = new StringBuffer();
                Iterator miter = messages.iterator();
                while (miter.hasNext()) {
                    fullString.append((String) miter.next());
                }
                Debug.logWarning(fullString.toString(), module);

                throw new IllegalArgumentException(fullString.toString());
            }
            
            return resultBool.booleanValue();
        }
    }
    
    public static class IfRegexp extends ScreenCondition {
        static PatternMatcher matcher = new Perl5Matcher();
        static PatternCompiler compiler = new Perl5Compiler();

        protected FlexibleMapAccessor fieldAcsr;
        protected FlexibleStringExpander exprExdr;
        
        public IfRegexp(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.fieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("field-name"));
            this.exprExdr = new FlexibleStringExpander(condElement.getAttribute("expr"));
        }
        
        public boolean eval(Map context) {
            Object fieldVal = this.fieldAcsr.get(context);
            String expr = this.exprExdr.expandString(context);
            Pattern pattern = null;
            try {
                pattern = compiler.compile(expr);
            } catch (MalformedPatternException e) {
                String errMsg = "Error in evaluation in if-regexp in screen: " + e.toString();
                Debug.logError(e, errMsg, module);
                throw new IllegalArgumentException(errMsg);
            }

            String fieldString = null;
            try {
                fieldString = (String) ObjectType.simpleTypeConvert(fieldVal, "String", null, null);
            } catch (GeneralException e) {
                Debug.logError(e, "Could not convert object to String, using empty String", module);
            }
            // always use an empty string by default
            if (fieldString == null) fieldString = "";
    
            return matcher.matches(fieldString, pattern);
        }
    }
    
    public static class IfEmpty extends ScreenCondition {
        protected FlexibleMapAccessor fieldAcsr;
        
        public IfEmpty(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.fieldAcsr = new FlexibleMapAccessor(condElement.getAttribute("field-name"));
        }
        
        public boolean eval(Map context) {
            Object fieldVal = this.fieldAcsr.get(context);
            return ObjectType.isEmpty(fieldVal);
        }
    }
    public static class IfEntityPermission extends ScreenCondition {
        protected EntityPermissionChecker permissionChecker;
        
        public IfEntityPermission(ModelScreen modelScreen, Element condElement) {
            super (modelScreen, condElement);
            this.permissionChecker = new EntityPermissionChecker(condElement);
        }
        
        public boolean eval(Map context) {
        	
        	boolean passed = permissionChecker.runPermissionCheck(context);
            return passed;
        }
    }
}

