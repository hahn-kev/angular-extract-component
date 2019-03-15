import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSRecursiveWalkingElementVisitor;
import org.angular2.lang.expr.psi.Angular2Action;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AngularEvent {
    Angular2Action action;
    String eventName;

    public AngularEvent(Angular2Action action) {
        this.action = action;
        JSCallExpression callExpression = FindFirstCallExpression();
        if (callExpression == null) return;
        eventName = callExpression.getMethodExpression().getText();
    }

    private @Nullable JSCallExpression FindFirstCallExpression() {
        final JSCallExpression[] callExpression = new JSCallExpression[1];
        action.acceptChildren(new JSRecursiveWalkingElementVisitor() {
            @Override
            public void visitJSCallExpression(JSCallExpression node) {
                callExpression[0] = node;
                stopWalking();
            }
        });
        return callExpression[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AngularEvent that = (AngularEvent) o;
        return action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action);
    }
}
