import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.Nullable;

public class CustomRefactoringSupport extends RefactoringSupportProvider {
    public CustomRefactoringSupport() {
    }

    @Override
    public @Nullable RefactoringActionHandler getExtractClassHandler() {
        return new ExtractComponentHandler();
    }
}
