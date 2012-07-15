package logacy.actions;

import logacy.Activator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class Toggle extends AbstractHandler implements IObjectActionDelegate {
	
	private MessageConsoleStream console;
	private IEditorPart editorPart;
	private IDocument doc;
	
	public Toggle() {}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		console = Activator.getConsole();
		editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().getActiveEditor();

		if (editorPart instanceof AbstractTextEditor) {
			ITextEditor editor = (ITextEditor) editorPart;
			IDocumentProvider dp = editor.getDocumentProvider();
			doc = dp.getDocument(editor.getEditorInput());
			
			boolean didReplace = false;
			
			
			try {
				String docString = doc.get(0, doc.getLength());
				int inserts = 0;
				
				FindReplaceDocumentAdapter find = new FindReplaceDocumentAdapter(doc);
				IRegion match = find.find(0, "/*\\sPyxisLog.", true, true, false, true);
				
				while(match != null){
					int logStartIndex = match.getOffset();
					match = find.find(logStartIndex, ");", true, true, false, false);
					int logEndIndex = match.getOffset() + match.getLength();
					
					int startLine = doc.getLineOfOffset(logStartIndex);
					int endLine = doc.getLineOfOffset(logEndIndex);
					
					for(int z = startLine; z <= endLine; z++){
						int offset = inserts + inserts + doc.getLineOffset(z);
						String begin = docString.substring(0, offset);
						String middle = docString.substring(offset, offset + doc.getLineLength(z));
						String end = docString.substring(offset + doc.getLineLength(z));
						if(middle.startsWith("//")){
							middle = middle.substring(2);
							inserts--;
						}
						else{
							middle = "//" + middle;
							inserts++;
						}
						didReplace = true;
						docString = new StringBuilder(begin).append(middle).append(end).toString();
						
					}
					
					match = find.find(logEndIndex, "/*\\sPyxisLog.", true, true, false, true);
				}
				if(didReplace)
					doc.replace(0, doc.getLength(), docString);
				else
					console.println("No log statements found");
			} catch (BadLocationException e) {
				console.println(e.getStackTrace().toString());
			}
		}
		
		return null;
	}

	@Override
	public void run(IAction action) {
		try {
			execute(null);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			console.println(e.getStackTrace().toString());
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {}
}
