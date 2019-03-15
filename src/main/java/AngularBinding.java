import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.resolve.JSTypeEvaluator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.angular2.lang.expr.psi.Angular2PipeArgumentsList;
import org.angular2.lang.expr.psi.Angular2PipeReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AngularBinding {
    public final Collection<JSReferenceExpression> referenceExpressions;
    public final JSElement element;
    public boolean isTwoWayBinding;
    public final Collection<JSCallExpression> callExpressions;
    public final Set<InputField> inputFields;

    public AngularBinding(JSElement element, boolean isTwoWayBinding) {
        this.element = element;
        this.isTwoWayBinding = isTwoWayBinding;
        callExpressions = FindTopLevelCallExpressions();
        referenceExpressions = FindTopLevelReferenceExpressions();

        inputFields = Stream.concat(
                referenceExpressions.stream().map(this::ToInputField),
                callExpressions.stream().map(this::ToInputField)
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Collection<JSReferenceExpression> FindTopLevelReferenceExpressions() {
        final Collection<JSReferenceExpression> referenceExpressions = new HashSet<>();
        element.acceptChildren(new JSRecursiveWalkingElementVisitor() {
            @Override
            public void visitJSReferenceExpression(JSReferenceExpression node) {
                JSReferenceExpression referenceExpression = FindFirstReferenceExpression(node);
                if (referenceExpression == null) return;
                referenceExpressions.add(referenceExpression);
            }
        });
        return referenceExpressions;
    }

    private JSReferenceExpression FindFirstReferenceExpression(JSReferenceExpression node) {
        if (node instanceof Angular2PipeReferenceExpression) return null;
        PsiElement element = node;
        while (element.getParent() instanceof JSReferenceExpression || element.getParent() instanceof JSCallExpression) {
            element = element.getParent();
        }
        while (element.getFirstChild() != null) {
            element = element.getFirstChild();
        }
        while (element.getParent() != node.getParent()) {
            element = element.getParent();
            if (element instanceof Angular2PipeReferenceExpression || element instanceof JSCallExpression)
                return null;
            if (IsBindableReferenceExpression(element))
                return (JSReferenceExpression) element;
        }
        return null;
    }

    private boolean IsBindableReferenceExpression(PsiElement element) {
        if (!(element instanceof JSReferenceExpression)) {
            return false;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof Angular2PipeArgumentsList) return true;
        if (IsComponentExpressionCall(parent)) {
            return false;
        }
        return true;
    }

    private boolean IsComponentExpressionCall(PsiElement element) {
        if (!(element instanceof JSCallExpression) && !(element instanceof JSArgumentList)) {
            return false;
        }
        if ((element instanceof JSArgumentList)) {
            element = element.getParent();
        }
        PsiElement prevSibling = element.getPrevSibling();
        if (prevSibling == null) return true;
        IElementType elementType = prevSibling.getNode().getElementType();
        return prevSibling == null;
    }

    private Collection<JSCallExpression> FindTopLevelCallExpressions() {
        final Collection<JSCallExpression> callExpressions = new HashSet<>();
        element.acceptChildren(new JSRecursiveWalkingElementVisitor() {
            @Override
            public void visitJSCallExpression(JSCallExpression node) {
                PsiElement element = node;
                while (element.getFirstChild() != null) {
                    element = element.getFirstChild();
                }
                while (node.getParent() != element && !(element instanceof JSCallExpression)) {
                    element = element.getParent();
                }
                if (element instanceof JSCallExpression)
                    callExpressions.add((JSCallExpression) element);
            }
        });
        return callExpressions;
    }

    private @Nullable InputField ToInputField(JSReferenceExpression referenceExpression) {
        PsiElement psiReference = referenceExpression.resolve();
        if (psiReference == null) {
            return TryToResolveReference(referenceExpression);
        }
        if (psiReference instanceof TypeScriptField) {
            TypeScriptField typeScriptField = (TypeScriptField) psiReference;
            JSType type = typeScriptField.getType();
            return new InputField(typeScriptField.getName(), type == null ? null : type.toString(), referenceExpression.getText(), isTwoWayBinding);
        }

        return null;
    }

    private InputField TryToResolveReference(JSReferenceExpression referenceExpression) {
        if (referenceExpression.getQualifier() != null) return null;
        //looks like it's a template
        String type = null;
        //todo determine field type
        JSTypeEvaluationResult elementType = JSTypeEvaluator.getElementType(referenceExpression);
        if (elementType != null) {
            JSType jsType = elementType.getType();
            if (jsType != null) type = jsType.toString();
            if ("*".equals(type)) type = "any";
        }
        return new InputField(referenceExpression.getReferenceName(), type, referenceExpression.getText(), isTwoWayBinding);
    }

    private InputField ToInputField(JSCallExpression callExpression) {
        //todo determine return type of method call
        return new InputField(callExpressionFieldName(callExpression), null, callExpression.getText());
    }

    public static String callExpressionFieldName(JSCallExpression callExpression) {
        return callExpression.getMethodExpression().getText();
    }

    public boolean isNotTwoWayBinding() {
        return !isTwoWayBinding;
    }

    public boolean isTwoWayBinding() {
        return isTwoWayBinding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AngularBinding that = (AngularBinding) o;
        return Objects.equals(element, that.element);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element);
    }
}