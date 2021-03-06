package org.scalaide.refactoring.internal.ui

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.refactoring.reorg.CreateTargetQueries
import org.eclipse.jdt.ui.JavaElementLabelProvider
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.SWT
import org.eclipse.ui.model.WorkbenchViewerComparator
import org.eclipse.ui.model.BaseWorkbenchContentProvider

class MoveClassRefactoringConfigurationPage(
  resourceToMove: IResource,
  singleImplSelected: Option[String],
  setPackageFragment: IPackageFragment => Unit,
  setMoveOnlySelectedClass: Boolean => Unit) extends UserInputWizardPage("MoveResourcesRefactoringConfigurationPage") {

  lazy val originatingPackage = {
    val javaProject = JavaCore.create(resourceToMove.getProject)
    val pf = javaProject.findPackageFragment(resourceToMove.getParent().getFullPath())
    pf.getElementName()
  }

  private var destinationField: TreeViewer = _
  private var moveSelectedClass: Button = _

  def createControl(parent: Composite): Unit = {
    initializeDialogUnits(parent)

    val composite = new Composite(parent, SWT.NONE)
    composite.setLayout(new GridLayout(2, false))
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    composite.setFont(parent.getFont)

    val label = new Label(composite, SWT.NONE)
    label.setText("&Choose destination for ''%s'':".format(resourceToMove.getName))
    label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    val button = new Button(composite, SWT.NONE)
    button.setText("Create New Package...")

    button.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent): Unit = {
        val createTargetQueries = new CreateTargetQueries(parent.getShell)
        val createdTarget = createTargetQueries.createNewPackageQuery.getCreatedTarget(destinationField.getSelection)
        destinationField.refresh()
        resourceToMove.getProject.refreshLocal(IResource.DEPTH_ONE, null)
        if(createdTarget != null)
          destinationField.setSelection(new StructuredSelection(createdTarget))
      }
    })

    destinationField = new TreeViewer(composite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER)
    val gd = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1)
    gd.widthHint = convertWidthInCharsToPixels(40)
    gd.heightHint = convertHeightInCharsToPixels(15)
    gd.grabExcessVerticalSpace = true
    gd.grabExcessHorizontalSpace = true
    destinationField.getTree.setLayoutData(gd)
    destinationField.setLabelProvider(new JavaElementLabelProvider)
    destinationField.setContentProvider(new BaseWorkbenchContentProvider)
    destinationField.setComparator(new WorkbenchViewerComparator)

    destinationField.setInput(JavaCore.create(ResourcesPlugin.getWorkspace.getRoot))

    destinationField.addFilter(new ViewerFilter {
      override def select(viewer: Viewer, parentElement: Object, element: Object): Boolean = {
        element match {
          case project: IJavaProject =>
            project.getProject.equals(resourceToMove.getProject)
          case pkg: IPackageFragmentRoot =>
            !pkg.isArchive && !pkg.isExternal && pkg.isOpen && !pkg.isReadOnly
          case pkg: IPackageFragment =>
            !pkg.isDefaultPackage
          case _ =>
            false
        }
      }
    })
    destinationField.expandAll
    destinationField.addSelectionChangedListener(new ISelectionChangedListener {
      var subsequentSelection = false
      def selectionChanged(event: SelectionChangedEvent): Unit = {
        if (subsequentSelection) {
          validatePage
        } else {
          // Don't validate the page on the first selection to
          // prevent showing an error message at the start.
          subsequentSelection = true
        }
      }
    })

    if (singleImplSelected.isDefined) {
      moveSelectedClass = new Button(composite, SWT.CHECK)
      moveSelectedClass.setSelection(true)
      setMoveOnlySelectedClass(true)
      moveSelectedClass.setText("Move \"" + singleImplSelected.get + "\" (uncheck to move the whole file)")
    }

    setPageComplete(false)
    setControl(composite)
  }

  override def setVisible(visible: Boolean): Unit = {
    if (visible) {
      destinationField.getTree.setFocus
      setErrorMessage(null) // no error messages until user interacts
    }
    super.setVisible(visible)
  }

  private def validatePage(): Unit = {
    val status = new RefactoringStatus

    getSelectedPackage match {
      case None =>
        status.addFatalError("Select a package.")
      case Some(pkg) if pkg.getElementName == originatingPackage =>
        status.addFatalError("Selected element is already in this package.")
      case _ =>
    }


    // warn if the selected impl is a sealed class!
    setPageComplete(status)
  }

  protected override def performFinish = {
    initializeRefactoring
    super.performFinish
  }

  override def getNextPage = {
    initializeRefactoring
    super.getNextPage
  }

  private def initializeRefactoring(): Unit = {
    getSelectedPackage foreach { pkg =>
      setPackageFragment(pkg)
      if (moveSelectedClass != null) {
        setMoveOnlySelectedClass(moveSelectedClass.getSelection)
      }
    }
  }

  private def getSelectedPackage = {
    destinationField.getSelection match {
      case selection: IStructuredSelection =>
        selection.getFirstElement match {
          case pkg: IPackageFragment => Some(pkg)
          case _ => None
        }
      case _ => None
    }
  }
}
