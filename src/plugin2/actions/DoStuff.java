package plugin2.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import plugin2.Activator;

/**
 * Sample code template for editor context menu
 * and editor text selection or file text processing.
 * @author augustli
 */
public class DoStuff implements IObjectActionDelegate {
  private static final String TITLE = "Do Stuff";
  private Shell shell;

  /**
   * Constructor
   */
  public DoStuff() {
    super();
  }

  /**
   * Get the shell object for use in prompting error message.
   * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
   */
  @Override
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    shell = targetPart.getSite().getShell();
  }

  /**
   * Get the selection text or text for whole file
   * and pass it to process for modification.
   * @see IActionDelegate#run(IAction)
   */
  @Override
  public void run(IAction action) {
    try {
      //get active editor
      IEditorPart editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
                       .getActivePage().getActiveEditor();

      if (editorPart instanceof AbstractTextEditor) {
        //check if there is text selection
    	int selectedTextOffset = 0;
        int length = 0;
        int endLineIndex = 0;
        String selectedText = null;
        IEditorSite iEditorSite = editorPart.getEditorSite();
        if (iEditorSite != null) {
          ISelectionProvider selectionProvider = iEditorSite.getSelectionProvider();
          if (selectionProvider != null) {
            ISelection iSelection = selectionProvider.getSelection();
            if (!iSelection.isEmpty()) {
              selectedText = ((ITextSelection) iSelection).getText();
              endLineIndex = ((ITextSelection) iSelection).getEndLine();
            }
          }
        }
        
        String textToReplace = "PyxisLog.e(Constants.PYXIS_LOG_TAG, \"" + selectedText + " = \" + " + selectedText + ");\n";
        
        length = textToReplace.length();
        
        ITextEditor editor = (ITextEditor) editorPart;
        IDocumentProvider dp = editor.getDocumentProvider();
        IDocument doc = dp.getDocument(editor.getEditorInput());
        int offset = doc.getLineOffset(endLineIndex); 
        
        int endLineLength = doc.getLineLength(endLineIndex);
        
        String endLine = doc.get(offset, endLineLength);
        
        int tabs = 0;
        for(int z = 0; z < endLineLength; z++){
        	if(endLine.charAt(z) == '\t')
        		textToReplace = "\t" + textToReplace;
        	
        }
        
        offset += doc.getLineLength(endLineIndex);
        
        doc.replace(offset, 0, textToReplace);
      }
    } catch (Exception e) {
      MessageDialog.openError(shell, TITLE, e.getMessage());
    }
  }



  /**
   * Skip implementation
   * @see IActionDelegate#selectionChanged(IAction, ISelection)
   */
  @Override
  public void selectionChanged(IAction action, ISelection selection) {
  }

}

