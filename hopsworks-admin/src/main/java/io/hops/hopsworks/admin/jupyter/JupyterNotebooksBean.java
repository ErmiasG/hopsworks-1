/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.hops.hopsworks.admin.jupyter;

import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsersFacade;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.jupyter.JupyterController;
import io.hops.hopsworks.common.jupyter.JupyterManager;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.persistence.entity.hdfs.user.HdfsUsers;
import io.hops.hopsworks.persistence.entity.jupyter.JupyterProject;
import io.hops.hopsworks.persistence.entity.user.Users;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ManagedBean(name = "JupyterNotebooks")
@RequestScoped
public class JupyterNotebooksBean implements Serializable {

  private static final Logger LOGGER = Logger.getLogger(JupyterNotebooksBean.class.getName());

  @Inject
  private JupyterManager jupyterManager;
  @EJB
  private HdfsUsersFacade hdfsUsersFacade;
  @EJB
  private JupyterController jupyterController;
  @EJB
  private HdfsUsersController hdfsUsersController;
  @EJB
  private UserFacade userFacade;
  @EJB
  ProjectFacade projectFacade;

  private String action;

  private List<JupyterProject> filteredNotebooks;

  private List<JupyterProject> allNotebooks;

  public void setFilteredNotebooks(List<JupyterProject> filteredNotebooks) {
    this.filteredNotebooks = filteredNotebooks;
  }

  public List<JupyterProject> getFilteredNotebooks() {
    return filteredNotebooks;
  }

  public void setAllNotebooks(List<JupyterProject> allNotebooks) {
    this.allNotebooks = allNotebooks;
  }

  public List<JupyterProject> getAllNotebooks() {
    this.allNotebooks = jupyterManager.getAllNotebooks();
    return this.allNotebooks;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getHdfsUser(JupyterProject notebook) {
    int hdfsId = notebook.getHdfsUserId();
    if (hdfsId == -1) {
      return "Orphaned";
    }
    HdfsUsers hdfsUser = hdfsUsersFacade.find(hdfsId);
    return hdfsUser.getName();
  }

  public String kill(JupyterProject notebook) {
    String hdfsUser = getHdfsUser(notebook);
    try {
      if (hdfsUser.compareTo("Orphaned") == 0) {
        jupyterController.shutdownOrphan(notebook.getPid(), notebook.getPort());
      } else {
        Users user = userFacade.findByUsername(hdfsUsersController.getUserName(hdfsUser));
        jupyterController.shutdown(notebook.getProjectId(), hdfsUser, user, notebook.getSecret(),
          notebook.getPid(), notebook.getPort());
      }
      FacesContext context = FacesContext.getCurrentInstance();
      context.addMessage(null, new FacesMessage("Successful", "Successfully killed Jupyter Notebook Server."));
    } catch (ServiceException ex) {
      Logger.getLogger(JupyterNotebooksBean.class.getName()).log(Level.SEVERE, null, ex);
      FacesContext context = FacesContext.getCurrentInstance();
      context.addMessage(null, new FacesMessage("Failure", "Failed to kill Jupyter Notebook Server."));
      return "KILL_NOTEBOOK_FAILED";
    }
    return "KILL_NOTEBOOK_SUCCESS";
  }

}
