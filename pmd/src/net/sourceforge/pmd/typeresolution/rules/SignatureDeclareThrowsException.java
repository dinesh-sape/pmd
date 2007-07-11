/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.typeresolution.rules;

import java.util.Arrays;
import java.util.List;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.ast.ASTExtendsList;
import net.sourceforge.pmd.ast.ASTImplementsList;
import net.sourceforge.pmd.ast.ASTImportDeclaration;
import net.sourceforge.pmd.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.ast.ASTName;
import net.sourceforge.pmd.ast.Node;
import net.sourceforge.pmd.ast.SimpleNode;

/**
 * A method/constructor shouldn't explicitly throw java.lang.Exception, since it
 * is unclear which exceptions that can be thrown from the methods. It might be
 * difficult to document and understand the vague interfaces. Use either a class
 * derived from RuntimeException or a checked exception. This version uses PMD's
 * type resolution facilities, and can detect if the class implements or extends
 * TestCase class
 * 
 * @author <a mailto:trondandersen@c2i.net>Trond Andersen</a>
 * @author acaplan
 * @author Wouter Zelle
 */
public class SignatureDeclareThrowsException extends AbstractRule {
    private boolean junitImported = false;

    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
        if (junitImported == true)
            return super.visit(node, data);

        ASTImplementsList impl = node.getFirstChildOfType(ASTImplementsList.class);
        if (impl != null && impl.jjtGetParent().equals(node)) {
            for (int ix = 0; ix < impl.jjtGetNumChildren(); ix++) {
                ASTClassOrInterfaceType type = (ASTClassOrInterfaceType) impl.jjtGetChild(ix);
                if (type.getType() == null) {
                    if ("junit.framework.Test".equals(type.getImage())) {
                        junitImported = true;
                        return super.visit(node, data);
                    }
                } else if (type.getType().equals(junit.framework.Test.class)) {
                    junitImported = true;
                    return super.visit(node, data);
                } else {
                    List implementors = Arrays.asList(type.getType().getInterfaces());
                    if (implementors.contains(junit.framework.Test.class)) {
                        junitImported = true;
                        return super.visit(node, data);
                    }
                }
            }
        }
        if (node.jjtGetNumChildren() != 0 && node.jjtGetChild(0).getClass().equals(ASTExtendsList.class)) {
            ASTClassOrInterfaceType type = (ASTClassOrInterfaceType) ((SimpleNode) node.jjtGetChild(0)).jjtGetChild(0);
            Class clazz = type.getType();
            if (clazz != null && clazz.equals(junit.framework.Test.class)) {
                junitImported = true;
                return super.visit(node, data);
            }
            while (clazz != null && !Object.class.equals(clazz)) {
                if (Arrays.asList(clazz.getInterfaces()).contains(junit.framework.Test.class)) {
                    junitImported = true;
                    return super.visit(node, data);
                }
                clazz = clazz.getSuperclass();
            }
        }

        return super.visit(node, data);
    }
    
    public Object visit(ASTImportDeclaration node, Object o) {
        if (node.getImportedName().indexOf("junit") != -1) {
            junitImported = true;
        }
        return super.visit(node, o);
    }
    

    public Object visit(ASTMethodDeclaration methodDeclaration, Object o) {
        if (junitImported && (methodDeclaration.getMethodName().equals("setUp") || methodDeclaration.getMethodName().equals("tearDown"))) {
            return super.visit(methodDeclaration, o);
        }

        checkExceptions(methodDeclaration, o);
        
        return super.visit(methodDeclaration, o);
    }
    
    public Object visit(ASTConstructorDeclaration constructorDeclaration, Object o) {
        checkExceptions(constructorDeclaration, o);
        
        return super.visit(constructorDeclaration, o);
    }

    /**
     * Search the list of thrown exceptions for Exception
     */
    private void checkExceptions(SimpleNode method, Object o) {
        List<ASTName> exceptionList = method.findChildrenOfType(ASTName.class);
        if (!exceptionList.isEmpty()) {
            evaluateExceptions(exceptionList, o);
        }
    }

    /**
     * Checks all exceptions for possible violation on the exception declaration.
     *
     * @param exceptionList containing all exception for declaration
     * @param context
     */
    private void evaluateExceptions(List<ASTName> exceptionList, Object context) {
        for (ASTName exception: exceptionList) {
            if (hasDeclaredExceptionInSignature(exception)) {
                addViolation(context, exception);
            }
        }
    }

    /**
     * Checks if the given value is defined as <code>Exception</code> and the parent is either
     * a method or constructor declaration.
     *
     * @param exception to evaluate
     * @return true if <code>Exception</code> is declared and has proper parents
     */
    private boolean hasDeclaredExceptionInSignature(ASTName exception) {
        return exception.hasImageEqualTo("Exception") && isParentSignatureDeclaration(exception);
    }
    
    /**
     * @param exception to evaluate
     * @return true if parent node is either a method or constructor declaration
     */
    private boolean isParentSignatureDeclaration(ASTName exception) {
        Node parent = exception.jjtGetParent().jjtGetParent();
        return parent instanceof ASTMethodDeclaration || parent instanceof ASTConstructorDeclaration;
    }
}