/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxIndicatorAware;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.request.mapper.parameter.INamedParameters.Type;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.resource.JQueryResourceReference;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.config.SecurityFilterConfig;
import org.geoserver.security.config.SecurityManagerConfig;
import org.geoserver.web.data.layer.LayerPage;
import org.geoserver.web.data.layer.NewLayerPage;
import org.geoserver.web.data.layergroup.LayerGroupEditPage;
import org.geoserver.web.data.resource.ResourceConfigurationPage;
import org.geoserver.web.data.store.NewDataPage;
import org.geoserver.web.data.store.StorePage;
import org.geoserver.web.data.workspace.WorkspaceEditPage;
import org.geoserver.web.data.workspace.WorkspaceNewPage;
import org.geoserver.web.data.workspace.WorkspacePage;
import org.geoserver.web.spring.security.GeoServerSession;
import org.geoserver.web.util.LocalizationsFinder;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.LoggedInUserLabel;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Base class for web pages in GeoServer web application.
 *
 * <ul>
 *   <li>The basic layout
 *   <li>An OO infrastructure for common elements location
 *   <li>An infrastructure for locating subpages in the Spring context and creating links
 * </ul>
 *
 * @author Andrea Aaime, The Open Planning Project
 * @author Justin Deoliveira, The Open Planning Project
 */
public class GeoServerBasePage extends WebPage implements IAjaxIndicatorAware {

    /** The id of the panel sitting in the page-header, right below the page description */
    protected static final String HEADER_PANEL = "headerPanel";

    protected static final Logger LOGGER = Logging.getLogger(GeoServerBasePage.class);

    protected static volatile GeoServerNodeInfo NODE_INFO;

    /** feedback panels to report errors and information. */
    protected FeedbackPanel topFeedbackPanel;

    protected FeedbackPanel bottomFeedbackPanel;

    /** page for this page to return to when the page is finished, could be null. */
    protected Page returnPage;

    /** page class for this page to return to when the page is finished, could be null. */
    protected Class<? extends Page> returnPageClass;

    public static final String VERSION_3 = "jquery/jquery-3.5.1.js";

    /** Optional search text used to filter sidebar workspaces. */
    private String workspaceSearch;

    protected GeoServerBasePage(final PageParameters parameters) {
        super(parameters);
        commonBaseInit();
    }

    protected GeoServerBasePage() {
        commonBaseInit();
    }

    protected void commonBaseInit() {
        // lookup for a pluggable favicon
        PackageResourceReference faviconReference = null;
        List<HeaderContribution> cssContribs = getGeoServerApplication().getBeansOfType(HeaderContribution.class);
        for (HeaderContribution csscontrib : cssContribs) {
            try {
                if (csscontrib.appliesTo(this)) {
                    PackageResourceReference ref = csscontrib.getFavicon();
                    if (ref != null) {
                        faviconReference = ref;
                    }
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Problem adding header contribution", t);
            }
        }

        // favicon
        if (faviconReference == null) {
            faviconReference = new PackageResourceReference(GeoServerBasePage.class, "favicon.ico");
        }
        String faviconUrl = RequestCycle.get().urlFor(faviconReference, null).toString();
        add(new ExternalLink("faviconLink", faviconUrl, null));

        // page title
        add(new Label("pageTitle", new LoadableDetachableModel<String>() {

            @Override
            protected String load() {
                return getPageTitle();
            }
        }));

        // login / logout stuff
        GeoServerSecurityManager securityManager = getGeoServerApplication().getSecurityManager();
        SecurityManagerConfig securityConfig = securityManager.getSecurityConfig();

        List<String> securityFiltersNames = securityConfig.getFilterChain().filtersFor("/web/**");
        List<String> securityFilterClassNames = new ArrayList<>();
        for (String name : securityFiltersNames) {
            try {
                SecurityFilterConfig config = securityManager.loadFilterConfig(name, true);
                securityFilterClassNames.add(config.getClassName());
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Could not load security filter config for " + name, e);
            }
        }

        final Authentication user = GeoServerSession.get().getAuthentication();
        final boolean anonymous = user == null || user instanceof AnonymousAuthenticationToken;

        // login forms that are actual forms
        List<LoginFormInfo> loginforms =
                filterByAuth(getGeoServerApplication().getBeansOfType(LoginFormInfo.class)).stream()
                        .filter(lf -> !lf.isJustUseExternalLink())
                        .collect(Collectors.toList());

        // login forms that are just external links
        List<LoginFormInfo> loginExternalLinks =
                filterByAuth(getGeoServerApplication().getBeansOfType(LoginFormInfo.class)).stream()
                        .filter(lf -> lf.isJustUseExternalLink())
                        .collect(Collectors.toList());

        // setup form-based login form
        add(new ListView<>("loginforms", loginforms) {
            @Override
            public void populateItem(ListItem<LoginFormInfo> item) {
                LoginFormInfo info = item.getModelObject();

                WebMarkupContainer loginForm = new WebMarkupContainer("loginform") {
                    @Override
                    protected void onComponentTag(org.apache.wicket.markup.ComponentTag tag) {
                        String loginPath = getResourcePath(info.getLoginPath());
                        tag.put("action", loginPath);
                        tag.put("method", info.getMethod());
                    }
                };

                Image image;
                if (info.getIcon() != null) {
                    image = new Image(
                            "link.icon", new PackageResourceReference(info.getComponentClass(), info.getIcon()));
                } else {
                    image = new Image(
                            "link.icon",
                            new PackageResourceReference(GeoServerBasePage.class, "img/icons/silk/door-in.png"));
                }

                loginForm.add(image);
                if (info.getTitleKey() != null && !info.getTitleKey().isEmpty()) {
                    loginForm.add(new Label("link.label", new StringResourceModel(info.getTitleKey(), null, null)));
                    image.add(AttributeModifier.replace("alt", new ParamResourceModel(info.getTitleKey(), null)));
                } else {
                    loginForm.add(new Label("link.label", ""));
                }

                LoginFormHTMLInclude include;
                if (info.getInclude() != null) {
                    include = new LoginFormHTMLInclude(
                            "login.include", new PackageResourceReference(info.getComponentClass(), info.getInclude()));
                } else {
                    include = new LoginFormHTMLInclude("login.include", null);
                }
                loginForm.add(include);

                item.add(loginForm);

                boolean filterInChain = false;
                for (String filterClassName : securityFilterClassNames) {
                    if (filterClassName.equals(info.getFilterClass().getName())) {
                        filterInChain = true;
                        break;
                    }
                }
                loginForm.setVisible(anonymous && filterInChain && info.isEnabled());
            }
        });

        // setup external-link-based login form
        add(new ListView<>("loginExternalLinks", loginExternalLinks) {
            @Override
            public void populateItem(ListItem<LoginFormInfo> item) {
                LoginFormInfo info = item.getModelObject();

                WebMarkupContainer loginForm = new WebMarkupContainer("loginform") {
                    @Override
                    protected void onComponentTag(org.apache.wicket.markup.ComponentTag tag) {
                        tag.put("href", getResourcePath(info.getLoginPath()));
                    }
                };

                Image image;
                if (info.getIcon() != null) {
                    image = new Image(
                            "link.icon", new PackageResourceReference(info.getComponentClass(), info.getIcon()));
                } else {
                    image = new Image(
                            "link.icon",
                            new PackageResourceReference(GeoServerBasePage.class, "img/icons/silk/door-in.png"));
                }

                loginForm.add(image);
                if (info.getTitleKey() != null && !info.getTitleKey().isEmpty()) {
                    loginForm.add(new Label("link.label", new StringResourceModel(info.getTitleKey(), null, null)));
                    image.add(AttributeModifier.replace("alt", new ParamResourceModel(info.getTitleKey(), null)));
                } else {
                    loginForm.add(new Label("link.label", ""));
                }

                item.add(loginForm);

                boolean filterInChain = false;
                for (String filterClassName : securityFilterClassNames) {
                    if (filterClassName.equals(info.getFilterClass().getName())) {
                        filterInChain = true;
                        break;
                    }
                }
                loginForm.setVisible(anonymous && filterInChain && info.isEnabled());
            }
        });

        // logout form
        WebMarkupContainer loggedInAsForm = new WebMarkupContainer("loggedinasform");
        loggedInAsForm.add(new LoggedInUserLabel("loggedInUsername"));
        loggedInAsForm.setVisible(!anonymous);
        add(loggedInAsForm);

        WebMarkupContainer logoutForm = new WebMarkupContainer("logoutform") {
            @Override
            protected void onComponentTag(org.apache.wicket.markup.ComponentTag tag) {
                String logoutPath = getResourcePath("j_spring_security_logout");
                tag.put("href", logoutPath);
            }
        };
        add(logoutForm);

        Image image = new Image(
                "link.icon", new PackageResourceReference(GeoServerBasePage.class, "img/icons/silk/door-out.png"));

        logoutForm.add(image);
        logoutForm.add(new Label("link.label", new StringResourceModel("logout", null, null)));
        image.add(AttributeModifier.replace("alt", new ParamResourceModel("logout", null)));
        logoutForm.setVisible(!anonymous);

        // home page link
        add(new BookmarkablePageLink<>("home", GeoServerHomePage.class));

        // dev buttons
        DeveloperToolbar devToolbar = new DeveloperToolbar("devButtons");
        add(devToolbar);
        devToolbar.setVisible(
                RuntimeConfigurationType.DEVELOPMENT.equals(getApplication().getConfigurationType()));

        @SuppressWarnings("unchecked")
        List<MenuPageInfo<GeoServerBasePage>> infos =
                (List) filterByAuth(getGeoServerApplication().getBeansOfType(MenuPageInfo.class));
        final Map<Category, List<MenuPageInfo<GeoServerBasePage>>> links = splitByCategory(infos);

        List<MenuPageInfo<GeoServerBasePage>> standalone =
                links.containsKey(null) ? links.get(null) : new ArrayList<>();
        links.remove(null);

        List<Category> categories = new ArrayList<>(links.keySet());
        Collections.sort(categories);

        add(new ListView<>("category", categories) {
            @Override
            public void populateItem(ListItem<Category> item) {
                Category category = item.getModelObject();
                createCategoryComponent(item, category, links);
            }
        });

        add(new ListView<>("standalone", standalone) {
            @Override
            public void populateItem(ListItem<MenuPageInfo<GeoServerBasePage>> item) {
                MenuPageInfo<GeoServerBasePage> info = item.getModelObject();
                BookmarkablePageLink<GeoServerBasePage> link =
                        new BookmarkablePageLink<>("link", info.getComponentClass());
                link.add(AttributeModifier.replace(
                        "title", new StringResourceModel(info.getDescriptionKey(), null, null)));
                link.add(new Label("link.label", new StringResourceModel(info.getTitleKey(), null, null)));
                item.add(link);
            }
        });

        add(topFeedbackPanel = new FeedbackPanel("topFeedback"));
        topFeedbackPanel.setOutputMarkupId(true);
        add(bottomFeedbackPanel = new FeedbackPanel("bottomFeedback"));
        bottomFeedbackPanel.setOutputMarkupId(true);

        add(new WebMarkupContainer(HEADER_PANEL));

        // allow the subclasses to initialize before getTitle/getDescription are called
        add(new Label("gbpTitle", new LoadableDetachableModel<String>() {

            @Override
            protected String load() {
                return getTitle();
            }
        }));
        Label gbpDescription = new Label("gbpDescription", new LoadableDetachableModel<String>() {

            @Override
            protected String load() {
                return getDescription();
            }
        });
        gbpDescription.setEscapeModelStrings(false);

        add(gbpDescription);

        // node id handling
        WebMarkupContainer container = new WebMarkupContainer("nodeIdContainer");
        add(container);
        String id = getNodeInfo().getId();
        Label label = new Label("nodeId", id);
        container.add(label);
        NODE_INFO.customize(container);
        if (id == null) {
            container.setVisible(false);
        }

        // locale switcher
        add(localeSwitcher());

        String selectedWorkspace = getSelectedWorkspaceName();
        boolean workspaceScopedSearch = selectedWorkspace != null && !selectedWorkspace.isBlank();

        // sidebar "New" menu actions (hidden for anonymous users)
        WebMarkupContainer sidebarNew = new WebMarkupContainer("sidebarNew");
        sidebarNew.setVisible(!anonymous);
        sidebarNew.add(new BookmarkablePageLink<>("addLayerLink", NewLayerPage.class));
        sidebarNew.add(new BookmarkablePageLink<>("addGroupLink", LayerGroupEditPage.class));
        sidebarNew.add(new BookmarkablePageLink<>("addStoreLink", NewDataPage.class));
        sidebarNew.add(new BookmarkablePageLink<>("addWorkspaceLink", WorkspaceNewPage.class));
        add(sidebarNew);

        WebMarkupContainer searchScopeGlobalIcon = new WebMarkupContainer("searchScopeGlobalIcon");
        searchScopeGlobalIcon.setVisible(!workspaceScopedSearch);
        add(searchScopeGlobalIcon);
        WebMarkupContainer searchScopeWorkspaceIcon = new WebMarkupContainer("searchScopeWorkspaceIcon");
        searchScopeWorkspaceIcon.setVisible(workspaceScopedSearch);
        add(searchScopeWorkspaceIcon);
        add(new Label("searchScopeLabel", workspaceScopedSearch ? selectedWorkspace : "GLOBAL"));

        // sidebar workspace search form (filters server-side list of workspaces on submit/enter)
        Form<Void> workspaceSearchForm = new Form<>("workspaceSearchForm");
        workspaceSearchForm.setOutputMarkupId(true);
        TextField<String> workspaceSearchField =
                new TextField<>("workspaceSearch", new PropertyModel<>(this, "workspaceSearch"));
        workspaceSearchField.add(AttributeModifier.replace(
                "placeholder",
                workspaceScopedSearch
                        ? "Search layers and layer groups..."
                        : "Search workspace, layers and layer groups..."));
        workspaceSearchForm.add(workspaceSearchField);
        add(workspaceSearchForm);

        // sidebar tree content (global/workspaces and layer lists)
        initializeSidebarContent();
    }

    private void initializeSidebarContent() {
        final String selectedWorkspace = getSelectedWorkspaceName();
        final String selectedLayer = getSelectedLayerName();

        add(
                new ListView<>("breadcrumbItems", new LoadableDetachableModel<List<BreadcrumbEntry>>() {
                    @Override
                    protected List<BreadcrumbEntry> load() {
                        return loadBreadcrumbEntries();
                    }
                }) {
                    @Override
                    protected void populateItem(ListItem<BreadcrumbEntry> item) {
                        BreadcrumbEntry entry = item.getModelObject();
                        boolean singleBreadcrumb =
                                getList() != null && getList().size() == 1;
                        boolean renderCurrentAsLink = entry.isCurrent() && singleBreadcrumb;

                        ExternalLink breadcrumbLink = new ExternalLink("breadcrumbLink", entry.getUrl());

                        WebMarkupContainer globalIcon = new WebMarkupContainer("globalIcon");
                        WebMarkupContainer workspaceIcon = new WebMarkupContainer("workspaceIcon");
                        WebMarkupContainer breadcrumbLayerIcon = new WebMarkupContainer("breadcrumbLayerIcon");

                        globalIcon.setVisible(entry.getKind() == BreadcrumbKind.GLOBAL);
                        workspaceIcon.setVisible(entry.getKind() == BreadcrumbKind.WORKSPACE);
                        breadcrumbLayerIcon.setVisible(entry.getKind() == BreadcrumbKind.LAYER);

                        if (entry.getKind() == BreadcrumbKind.LAYER) {
                            // Reuse the same icon logic as the sidebar, based on selected layer type
                            LayerInfo layerInfo = null;
                            for (LayerInfo layer : loadAllLayers()) {
                                if (layer.getName().equals(getSelectedLayerName())) {
                                    layerInfo = layer;
                                    break;
                                }
                            }
                            String iconVariant = getLayerIconCssClass(layerInfo);
                            if (!iconVariant.isEmpty()) {
                                breadcrumbLayerIcon.add(AttributeModifier.append("class", iconVariant));
                            }
                        }

                        breadcrumbLink.add(globalIcon);
                        breadcrumbLink.add(workspaceIcon);
                        breadcrumbLink.add(breadcrumbLayerIcon);
                        breadcrumbLink.add(new Label("label", entry.getLabel()));
                        breadcrumbLink.setVisible(!entry.isCurrent() || renderCurrentAsLink);
                        item.add(breadcrumbLink);

                        WebMarkupContainer breadcrumbCurrent = new WebMarkupContainer("breadcrumbCurrent");
                        breadcrumbCurrent.setVisible(entry.isCurrent() && !renderCurrentAsLink);

                        WebMarkupContainer currentGlobalIcon = new WebMarkupContainer("currentGlobalIcon");
                        WebMarkupContainer currentWorkspaceIcon = new WebMarkupContainer("currentWorkspaceIcon");
                        WebMarkupContainer currentLayerIcon = new WebMarkupContainer("currentLayerIcon");

                        currentGlobalIcon.setVisible(entry.getKind() == BreadcrumbKind.GLOBAL);
                        currentWorkspaceIcon.setVisible(entry.getKind() == BreadcrumbKind.WORKSPACE);
                        currentLayerIcon.setVisible(entry.getKind() == BreadcrumbKind.LAYER);

                        if (entry.getKind() == BreadcrumbKind.LAYER) {
                            LayerInfo layerInfo = null;
                            for (LayerInfo layer : loadAllLayers()) {
                                if (layer.getName().equals(getSelectedLayerName())) {
                                    layerInfo = layer;
                                    break;
                                }
                            }
                            String iconVariant = getLayerIconCssClass(layerInfo);
                            if (!iconVariant.isEmpty()) {
                                currentLayerIcon.add(AttributeModifier.append("class", iconVariant));
                            }
                        }

                        breadcrumbCurrent.add(currentGlobalIcon);
                        breadcrumbCurrent.add(currentWorkspaceIcon);
                        breadcrumbCurrent.add(currentLayerIcon);
                        breadcrumbCurrent.add(new Label("currentLabel", entry.getLabel()));

                        List<BreadcrumbOption> options = Collections.emptyList();
                        if (entry.isCurrent() && !renderCurrentAsLink) {
                            if (selectedLayer != null) {
                                options = LAYER_BREADCRUMB_OPTIONS;
                            } else if (selectedWorkspace != null) {
                                options = WORKSPACE_BREADCRUMB_OPTIONS;
                            }
                        }

                        breadcrumbCurrent.add(new ListView<>("breadcrumbCurrentOptions", options) {
                            @Override
                            protected void populateItem(ListItem<BreadcrumbOption> optionItem) {
                                BreadcrumbOption option = optionItem.getModelObject();

                                if (option.isSeparator()) {
                                    optionItem.add(
                                            AttributeModifier.append("class", "gs-breadcrumb-option--with-separator"));
                                }

                                Label heading = new Label("optionHeading", option.getLabel());
                                heading.setVisible(option.isHeading());
                                optionItem.add(heading);

                                String optionUrl = GeoServerBasePage.this.buildBreadcrumbOptionUrl(
                                        option, selectedWorkspace, selectedLayer);
                                ExternalLink optionLink = new ExternalLink("optionLink", optionUrl);
                                optionLink.setVisible(!option.isHeading());
                                optionLink.add(new Label("optionLabel", option.getLabel()));

                                String valueText = option.getValue() != null ? option.getValue() : "";
                                Label valueLabel = new Label("optionValue", valueText);
                                valueLabel.setVisible(option.getValue() != null && !option.isHeading());
                                optionLink.add(valueLabel);

                                optionLink.add(AttributeModifier.replace("data-action", option.getActionKey()));
                                if (option.isDestructive()) {
                                    optionLink.add(AttributeModifier.append("class", "is-destructive"));
                                }

                                optionItem.add(optionLink);
                            }
                        });

                        item.add(breadcrumbCurrent);

                        if (entry.isCurrent()) {
                            item.add(AttributeModifier.replace("aria-current", "page"));
                        }
                    }
                });

        WebMarkupContainer globalToggle = new WebMarkupContainer("globalToggle");
        globalToggle.add(new Label("globalLabel", "GLOBAL"));
        globalToggle.add(new Label("globalCount", new LoadableDetachableModel<Integer>() {
            @Override
            protected Integer load() {
                return loadGlobalLayerNames().size();
            }
        }));
        add(globalToggle);

        WebMarkupContainer pinnedSectionHeading = new WebMarkupContainer("pinnedSectionHeading");
        pinnedSectionHeading.add(new Label("pinnedLabel", "PINNED"));
        pinnedSectionHeading.add(new Label("pinnedCount", new LoadableDetachableModel<Integer>() {
            @Override
            protected Integer load() {
                return loadPinnedLayerNames().size();
            }
        }));
        add(pinnedSectionHeading);

        WebMarkupContainer workspacesToggle = new WebMarkupContainer("workspacesToggle");
        workspacesToggle.add(new Label("workspacesLabel", "WORKSPACES"));
        workspacesToggle.add(new Label("workspacesCount", new LoadableDetachableModel<Integer>() {
            @Override
            protected Integer load() {
                return getCatalog().getWorkspaces().size();
            }
        }));
        add(workspacesToggle);

        add(
                new ListView<>("globalLayers", new LoadableDetachableModel<List<String>>() {
                    @Override
                    protected List<String> load() {
                        return loadGlobalLayerNames();
                    }
                }) {
                    @Override
                    protected void populateItem(ListItem<String> item) {
                        BookmarkablePageLink<LayerPage> link = new BookmarkablePageLink<>("layerLink", LayerPage.class);
                        link.add(new Label("layerName", item.getModel()));
                        if (selectedLayer != null && selectedLayer.equals(item.getModelObject())) {
                            link.add(AttributeModifier.append("class", "is-active"));
                        }
                        item.add(link);
                    }
                });

        add(
                new ListView<>("workspaceItems", new LoadableDetachableModel<List<SidebarWorkspaceEntry>>() {
                    @Override
                    protected List<SidebarWorkspaceEntry> load() {
                        return loadWorkspaceEntries();
                    }
                }) {
                    @Override
                    protected void populateItem(ListItem<SidebarWorkspaceEntry> item) {
                        SidebarWorkspaceEntry entry = item.getModelObject();
                        boolean workspaceActive =
                                selectedWorkspace != null && selectedWorkspace.equals(entry.getName());

                        WebMarkupContainer toggle = new WebMarkupContainer("workspaceToggle");
                        toggle.add(AttributeModifier.replace("aria-controls", entry.getListId()));
                        toggle.add(AttributeModifier.replace("aria-expanded", workspaceActive ? "true" : "false"));
                        if (workspaceActive) {
                            toggle.add(AttributeModifier.append("class", "is-active"));
                        }

                        BookmarkablePageLink<GeoServerHomePage> workspaceLink = new BookmarkablePageLink<>(
                                "workspaceLink", GeoServerHomePage.class, createHomePageParams(entry.getName(), null));
                        workspaceLink.add(new Label("workspaceLabel", entry.getName()));
                        toggle.add(workspaceLink);
                        toggle.add(new Label(
                                "workspaceCount", entry.getPublishedEntries().size()));
                        item.add(toggle);

                        WebMarkupContainer layersContainer = new WebMarkupContainer("workspaceLayersContainer");
                        layersContainer.add(AttributeModifier.replace("id", entry.getListId()));
                        if (!workspaceActive) {
                            layersContainer.add(AttributeModifier.replace("hidden", "hidden"));
                        }
                        item.add(layersContainer);

                        ListView<SidebarPublishedEntry> layers =
                                new ListView<>("workspaceLayers", entry.getPublishedEntries()) {
                                    @Override
                                    protected void populateItem(ListItem<SidebarPublishedEntry> layerItem) {
                                        SidebarPublishedEntry published = layerItem.getModelObject();

                                        BookmarkablePageLink<LayerPage> link = new BookmarkablePageLink<>(
                                                "layerLink",
                                                GeoServerHomePage.class,
                                                createHomePageParams(entry.getName(), published.getName()));

                                        WebMarkupContainer layerIcon = new WebMarkupContainer("layerIcon");
                                        String iconVariant = published.getIconCssClass();
                                        if (!iconVariant.isEmpty()) {
                                            layerIcon.add(AttributeModifier.append("class", iconVariant));
                                        }
                                        link.add(layerIcon);

                                        link.add(new Label("layerName", published.getName()));
                                        if (selectedLayer != null && selectedLayer.equals(published.getName())) {
                                            link.add(AttributeModifier.append("class", "is-active"));
                                        }
                                        layerItem.add(link);
                                    }
                                };
                        layersContainer.add(layers);
                    }
                });
    }

    private PageParameters createHomePageParams(String workspace, String layer) {
        PageParameters params = new PageParameters();
        if (workspace != null && !workspace.isBlank()) {
            params.add("workspace", workspace, 0, Type.QUERY_STRING);
        }
        if (layer != null && !layer.isBlank()) {
            params.add("layer", layer, 1, Type.QUERY_STRING);
        }
        return params;
    }

    private List<String> loadGlobalLayerNames() {
        List<String> names = new ArrayList<>();
        for (LayerInfo layer : loadAllLayers()) {
            String prefixed = layer.prefixedName();
            if (!prefixed.contains(":")) {
                names.add(prefixed);
            }
        }
        Collections.sort(names);
        return names;
    }

    private List<String> loadPinnedLayerNames() {
        List<String> names = new ArrayList<>();
        for (LayerInfo layer : loadAllLayers()) {
            boolean isEnabled = layer.isEnabled();
            boolean isAdvertised = layer.isAdvertised();
            if (isEnabled && isAdvertised) {
                names.add(layer.prefixedName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private List<SidebarWorkspaceEntry> loadWorkspaceEntries() {
        Catalog catalog = getCatalog();
        List<WorkspaceInfo> workspaces = new ArrayList<>(catalog.getWorkspaces());
        String query = workspaceSearch;
        if (query != null && !query.isBlank()) {
            String lowered = query.toLowerCase(Locale.ROOT);
            workspaces.removeIf(ws -> ws.getName() == null
                    || !ws.getName().toLowerCase(Locale.ROOT).contains(lowered));
        }
        workspaces.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));

        List<SidebarWorkspaceEntry> entries = new ArrayList<>(workspaces.size());
        for (int i = 0; i < workspaces.size(); i++) {
            WorkspaceInfo workspace = workspaces.get(i);
            List<SidebarPublishedEntry> publishedEntries = loadWorkspacePublishedEntries(workspace);
            entries.add(new SidebarWorkspaceEntry(workspace.getName(), "gs-workspace-layers-" + i, publishedEntries));
        }

        return entries;
    }

    private List<SidebarPublishedEntry> loadWorkspacePublishedEntries(WorkspaceInfo workspace) {
        String workspacePrefix = workspace.getName() + ":";
        List<SidebarPublishedEntry> entries = new ArrayList<>();
        for (LayerInfo layer : loadAllLayers()) {
            String prefixed = layer.prefixedName();
            if (prefixed.startsWith(workspacePrefix)) {
                entries.add(new SidebarPublishedEntry(layer.getName(), getLayerIconCssClass(layer)));
            }
        }
        for (LayerGroupInfo group : getCatalog().getLayerGroupsByWorkspace(workspace)) {
            entries.add(new SidebarPublishedEntry(group.getName(), getLayerGroupIconCssClass()));
        }
        entries.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return entries;
    }

    private String getLayerIconCssClass(LayerInfo layerInfo) {
        if (layerInfo == null || layerInfo.getType() == null) {
            return "";
        }

        switch (layerInfo.getType()) {
            case RASTER:
                return "gs-layer-icon--raster";
            case VECTOR:
                try {
                    FeatureTypeInfo fti = (FeatureTypeInfo) layerInfo.getResource();
                    Class<?> geom = fti.getFeatureType()
                            .getGeometryDescriptor()
                            .getType()
                            .getBinding();
                    if (Point.class.isAssignableFrom(geom) || MultiPoint.class.isAssignableFrom(geom)) {
                        return "gs-layer-icon--point";
                    } else if (LineString.class.isAssignableFrom(geom)
                            || MultiLineString.class.isAssignableFrom(geom)) {
                        return "gs-layer-icon--line";
                    } else if (Polygon.class.isAssignableFrom(geom) || MultiPolygon.class.isAssignableFrom(geom)) {
                        return "gs-layer-icon--polygon";
                    }
                    return "";
                } catch (Exception e) {
                    return "";
                }
            default:
                return "";
        }
    }

    private String getLayerGroupIconCssClass() {
        return "gs-layer-icon--group";
    }

    private List<LayerInfo> loadAllLayers() {
        return new ArrayList<>(getCatalog().getLayers());
    }

    private List<BreadcrumbEntry> loadBreadcrumbEntries() {
        String selectedWorkspace = getSelectedWorkspaceName();
        String selectedLayer = getSelectedLayerName();

        List<BreadcrumbEntry> entries = new ArrayList<>();
        boolean globalCurrent = selectedWorkspace == null && selectedLayer == null;
        PageParameters globalParams = new PageParameters();
        String globalUrl = urlFor(GeoServerHomePage.class, globalParams).toString();
        entries.add(new BreadcrumbEntry("Global", globalUrl, globalCurrent, BreadcrumbKind.GLOBAL));

        if (selectedWorkspace != null) {
            PageParameters workspaceParams = new PageParameters();
            workspaceParams.add("workspace", selectedWorkspace);
            boolean workspaceCurrent = selectedLayer == null;
            String workspaceUrl =
                    urlFor(GeoServerHomePage.class, workspaceParams).toString();
            entries.add(
                    new BreadcrumbEntry(selectedWorkspace, workspaceUrl, workspaceCurrent, BreadcrumbKind.WORKSPACE));
        }

        if (selectedLayer != null) {
            PageParameters layerParams = new PageParameters();
            if (selectedWorkspace != null) {
                layerParams.add("workspace", selectedWorkspace);
            }
            layerParams.add("layer", selectedLayer);
            String layerUrl = urlFor(GeoServerHomePage.class, layerParams).toString();
            entries.add(new BreadcrumbEntry(selectedLayer, layerUrl, true, BreadcrumbKind.LAYER));
        }

        return entries.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private String getSelectedWorkspaceName() {
        var params = getRequest().getRequestParameters();

        // Standard sidebar / URL param
        String workspace = params.getParameterValue("workspace").toOptionalString();
        if (workspace != null && !workspace.isBlank()) {
            return workspace;
        }

        // Layer configuration pages often use "wsName" instead of "workspace"
        String wsName = params.getParameterValue("wsName").toOptionalString();
        if (wsName != null && !wsName.isBlank()) {
            return wsName;
        }

        // Workspace-centric pages (e.g. WorkspaceEditPage) use "name" for the workspace
        String name = params.getParameterValue("name").toOptionalString();
        if (name != null && !name.isBlank() && (this instanceof WorkspaceEditPage || this instanceof WorkspacePage)) {
            return name;
        }

        return null;
    }

    private String getSelectedLayerName() {
        String layer =
                getRequest().getRequestParameters().getParameterValue("layer").toOptionalString();
        String name =
                getRequest().getRequestParameters().getParameterValue("name").toOptionalString();
        String selectedLayer = layer != null && !layer.isBlank() ? layer : name;

        // On workspace-centric pages, the "name" parameter refers to a workspace,
        // not a layer. In those cases we should not treat it as a selected layer,
        // otherwise the breadcrumb will incorrectly show layer options.
        if ((this instanceof WorkspaceEditPage || this instanceof WorkspacePage)
                && (layer == null || layer.isBlank())) {
            selectedLayer = null;
        }
        if (selectedLayer == null || selectedLayer.isBlank()) {
            return null;
        }
        if (selectedLayer.contains(":")) {
            return selectedLayer.substring(selectedLayer.indexOf(':') + 1);
        }
        return selectedLayer;
    }

    private String buildBreadcrumbOptionUrl(BreadcrumbOption option, String selectedWorkspace, String selectedLayer) {
        String action = option.getActionKey();
        if (action == null) {
            return "#";
        }

        // Workspace-scoped actions
        if (action.startsWith("workspace.")) {
            if (selectedWorkspace == null) {
                return "#";
            }

            PageParameters params = new PageParameters();

            if ("workspace.data.allLayers".equals(action)) {
                params.add("workspace", selectedWorkspace);
                return urlFor(LayerPage.class, params).toString();
            }

            if ("workspace.data.stores".equals(action)) {
                params.add("workspace", selectedWorkspace);
                return urlFor(StorePage.class, params).toString();
            }

            // Default: workspace edit page (various sections/tabs)
            params.add("name", selectedWorkspace);
            return urlFor(WorkspaceEditPage.class, params).toString();
        }

        // Layer-scoped actions
        if (action.startsWith("layer.")) {
            if (selectedLayer == null) {
                return "#";
            }

            PageParameters params = new PageParameters();
            if (selectedWorkspace != null) {
                params.add("workspace", selectedWorkspace);
            }
            params.add("name", selectedLayer);

            return urlFor(ResourceConfigurationPage.class, params).toString();
        }

        return "#";
    }

    private static class SidebarWorkspaceEntry {
        private final String name;
        private final String listId;
        private final List<SidebarPublishedEntry> publishedEntries;

        SidebarWorkspaceEntry(String name, String listId, List<SidebarPublishedEntry> publishedEntries) {
            this.name = name;
            this.listId = listId;
            this.publishedEntries = publishedEntries;
        }

        String getName() {
            return name;
        }

        String getListId() {
            return listId;
        }

        List<SidebarPublishedEntry> getPublishedEntries() {
            return publishedEntries;
        }
    }

    private static class SidebarPublishedEntry {
        private final String name;
        private final String iconCssClass;

        SidebarPublishedEntry(String name, String iconCssClass) {
            this.name = name;
            this.iconCssClass = iconCssClass;
        }

        String getName() {
            return name;
        }

        String getIconCssClass() {
            return iconCssClass;
        }
    }

    private enum BreadcrumbKind {
        GLOBAL,
        WORKSPACE,
        LAYER
    }

    private static class BreadcrumbEntry {
        private final String label;
        private final String url;
        private final boolean current;
        private final BreadcrumbKind kind;

        BreadcrumbEntry(String label, String url, boolean current, BreadcrumbKind kind) {
            this.label = label;
            this.url = url;
            this.current = current;
            this.kind = kind;
        }

        String getLabel() {
            return label;
        }

        String getUrl() {
            return url;
        }

        boolean isCurrent() {
            return current;
        }

        BreadcrumbKind getKind() {
            return kind;
        }
    }

    private static class BreadcrumbOption {
        private final String label;
        private final String actionKey;
        private final boolean heading;
        private final String value;
        private final boolean destructive;
        private final boolean separator;

        BreadcrumbOption(String label, String actionKey) {
            this(label, actionKey, false, null, false, false);
        }

        BreadcrumbOption(String label, String actionKey, boolean heading, String value, boolean destructive) {
            this(label, actionKey, heading, value, destructive, false);
        }

        BreadcrumbOption(
                String label, String actionKey, boolean heading, String value, boolean destructive, boolean separator) {
            this.label = label;
            this.actionKey = actionKey;
            this.heading = heading;
            this.value = value;
            this.destructive = destructive;
            this.separator = separator;
        }

        String getLabel() {
            return label;
        }

        String getActionKey() {
            return actionKey;
        }

        boolean isHeading() {
            return heading;
        }

        String getValue() {
            return value;
        }

        boolean isDestructive() {
            return destructive;
        }

        boolean isSeparator() {
            return separator;
        }
    }

    private static final List<BreadcrumbOption> WORKSPACE_BREADCRUMB_OPTIONS = List.of(
            new BreadcrumbOption("Workspace", "workspace.section", true, null, false),
            new BreadcrumbOption("Edit Settings (Name, URI)", "workspace.editSettings"),
            new BreadcrumbOption("Contact Information", "workspace.contactInformation"),
            new BreadcrumbOption("Security (Data Access Rules)", "workspace.security"),
            new BreadcrumbOption("Isolated Workspace", "workspace.isolated"),
            new BreadcrumbOption("Service Overrides", "workspace.section.serviceOverrides", true, null, false, true),
            new BreadcrumbOption("WMS Settings", "workspace.wmsOverride", false, "override", false),
            new BreadcrumbOption("WFS Settings", "workspace.wfsOverride", false, "override", false),
            new BreadcrumbOption("WCS Settings", "workspace.wcsOverride", false, "override", false),
            new BreadcrumbOption("Data", "workspace.section.data", true, null, false, true),
            new BreadcrumbOption("All Layers", "workspace.data.allLayers", false, "62", false),
            new BreadcrumbOption("Stores", "workspace.data.stores"),
            new BreadcrumbOption("Layer Preview", "workspace.data.layerPreview"),
            new BreadcrumbOption("Remove Workspace", "workspace.remove", false, null, true, true));

    private static final List<BreadcrumbOption> LAYER_BREADCRUMB_OPTIONS = List.of(
            new BreadcrumbOption("Layer", "layer.section", true, null, false),
            new BreadcrumbOption("Edit Layer (Data)", "layer.editData"),
            new BreadcrumbOption("Publishing Settings", "layer.publishing"),
            new BreadcrumbOption("Layer Security", "layer.security"),
            new BreadcrumbOption("Edit SLD Style", "layer.editStyle"),
            new BreadcrumbOption("Preview", "layer.section.preview", true, null, false, true),
            new BreadcrumbOption("OpenLayers", "layer.preview.openlayers"),
            new BreadcrumbOption("KML / KMZ", "layer.preview.kml"),
            new BreadcrumbOption("Output Formats (WFS)", "layer.section.outputFormats", true, null, false, true),
            new BreadcrumbOption("GML 3.2", "layer.output.wfs.gml", false, "xml", false),
            new BreadcrumbOption("GeoJSON", "layer.output.wfs.geojson", false, "json", false),
            new BreadcrumbOption("CSV", "layer.output.wfs.csv", false, "csv", false),
            new BreadcrumbOption("Shapefile", "layer.output.wfs.shp", false, "shp", false),
            new BreadcrumbOption("Image Formats (WMS)", "layer.section.imageFormats", true, null, false, true),
            new BreadcrumbOption("PNG / JPEG / TIFF / GIF / SVG / PDF", "layer.output.wms.images", false, null, false),
            new BreadcrumbOption("Tile Caching", "layer.caching", false, null, false, true),
            new BreadcrumbOption("Dimensions (Time / Elevation)", "layer.dimensions"),
            new BreadcrumbOption("Remove Layer", "layer.remove", false, null, true, true));

    private Component localeSwitcher() {
        // defaults to English to have a more compact dropdown
        DropDownChoice<Locale> select =
                new DropDownChoice<>("localeSwitcher", new Model<>(getSessionLocale()), getLocalesModel());
        select.add(new AjaxFormComponentUpdatingBehavior("change") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                Locale locale = select.getModelObject();
                // null is used for reset to browser settings
                if (locale == null) {
                    // clear the cookie
                    Cookie languageCookie = new Cookie(GeoServerApplication.LANGUAGE_COOKIE_NAME, null);
                    ((WebResponse) getResponse()).clearCookie(languageCookie);

                    // get the language from request
                    locale = target.getPage().getRequest().getLocale();
                    if (locale == null) locale = Locale.ENGLISH;
                } else {
                    // explicit choice, save to cookie
                    getGeoServerApplication().refreshLocaleCookie(getResponse(), locale);
                }

                // by now locale has been set to a non-null value, stick in the session too
                getSession().setLocale(locale);
                target.add(getPage());
            }
        });
        return select;
    }

    private List<Locale> getLocalesModel() {
        List<Locale> model = new ArrayList<>();
        model.add(null); // to reset the choice and go back to browser language
        model.addAll(LocalizationsFinder.getAvailableLocales());
        return model;
    }

    /**
     * Returns the locale held in the session, if it's one of the locales supported by GeoServer
     *
     * @return
     */
    private Locale getSessionLocale() {
        Locale locale = getSession().getLocale();
        if (locale == null) return Locale.ENGLISH;

        // exact match?
        List<Locale> locales = LocalizationsFinder.getAvailableLocales();
        if (locales.contains(locale)) return locale;

        // maybe a match just on the language then?
        return locales.stream()
                .filter(l -> locale.getLanguage().equals(l.getLanguage()))
                .findFirst()
                .orElse(Locale.ENGLISH);
    }

    private void createCategoryComponent(
            ListItem<Category> item, Category category, Map<Category, List<MenuPageInfo<GeoServerBasePage>>> links) {
        item.add(new Label("category.header", new StringResourceModel(category.getNameKey(), null, null)));
        item.add(new ListView<>("category.links", links.get(category)) {
            @Override
            public void populateItem(ListItem<MenuPageInfo<GeoServerBasePage>> item) {
                createMenuComponent(item);
            }
        });
    }

    private void createMenuComponent(ListItem<MenuPageInfo<GeoServerBasePage>> item) {
        MenuPageInfo<GeoServerBasePage> info = item.getModelObject();
        BookmarkablePageLink<Page> link = new BookmarkablePageLink<>("link", info.getComponentClass()) {

            @Override
            public PageParameters getPageParameters() {
                PageParameters pageParams = super.getPageParameters();
                pageParams.add(GeoServerTablePanel.FILTER_PARAM, false, Type.PATH);
                return pageParams;
            }
        };

        link.add(AttributeModifier.replace("title", new StringResourceModel(info.getDescriptionKey(), null, null)));
        link.add(new Label("link.label", new StringResourceModel(info.getTitleKey(), null, null)));
        Image image;
        if (info.getIcon() != null) {
            image = new Image("link.icon", new PackageResourceReference(info.getComponentClass(), info.getIcon()));
        } else {
            image = new Image(
                    "link.icon", new PackageResourceReference(GeoServerBasePage.class, "img/icons/silk/wrench.png"));
        }
        image.add(AttributeModifier.replace("alt", new ParamResourceModel(info.getTitleKey(), null)));
        link.add(image);
        item.add(link);
    }

    private String getResourcePath(String path) {
        HttpServletRequest hr = ((GeoServerApplication) getApplication()).servletRequest(getRequest());
        String baseURL = ResponseUtils.baseURL(hr);
        return ResponseUtils.buildURL(baseURL, path, null, URLMangler.URLType.RESOURCE);
    }

    @Override
    public void renderHead(IHeaderResponse response) {

        // includes jquery, required by the placeholder plugin (wicket only include jquery if he
        // need it)
        response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(JQueryResourceReference.INSTANCE_3)));
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "css/blueprint/print.css"), "print"));
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "css/geoserver.css"), "screen, projection"));
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "js/geoserver.js")));
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "js/jquery.placeholder.js")));
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "js/jquery.fullscreen.js")));

        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(GeoServerBasePage.class, "js/jquery.hide.ajaxFeedback.js")));

        // due to Content-security-policy, JS must be rendered by Wicket.  This inits the textboxes
        // for placeholders.
        response.render(OnDomReadyHeaderItem.forScript("$('input, textarea').placeholder();"));

        List<HeaderContribution> cssContribs = getGeoServerApplication().getBeansOfType(HeaderContribution.class);
        for (HeaderContribution csscontrib : cssContribs) {
            try {
                if (csscontrib.appliesTo(this)) {
                    PackageResourceReference ref = csscontrib.getCSS();
                    if (ref != null) {
                        response.render(CssReferenceHeaderItem.forReference(ref));
                    }

                    ref = csscontrib.getJavaScript();
                    if (ref != null) {
                        response.render(JavaScriptHeaderItem.forReference(ref));
                    }

                    ref = csscontrib.getFavicon();
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Problem adding header contribution", t);
            }
        }
    }

    private GeoServerNodeInfo getNodeInfo() {
        // we don't synch on this one, worst it can happen, we create
        // two instances of DefaultGeoServerNodeInfo, and one wil be gc-ed soon
        if (NODE_INFO == null) {
            // see if someone plugged a custom node info bean, otherwise use the default one
            GeoServerNodeInfo info = GeoServerExtensions.bean(GeoServerNodeInfo.class);
            if (info == null) {
                info = new DefaultGeoServerNodeInfo();
            }
            NODE_INFO = info;
        }

        return NODE_INFO;
    }

    protected String getTitle() {
        return new ParamResourceModel("title", this).getString();
    }

    protected String getDescription() {
        return new ParamResourceModel("description", this).getString();
    }

    /** Gets the page title from the PageName.title resource, falling back on "GeoServer" if not found */
    String getPageTitle() {
        try {
            return "GeoServer: " + getTitle();
        } catch (Exception e) {
            LOGGER.warning(getClass().getSimpleName() + " does not have a title set");
        }
        return "GeoServer";
    }

    /**
     * The base page is built with an empty panel in the page-header section that can be filled by subclasses calling
     * this method
     *
     * @param component The component to be placed at the bottom of the page-header section. The component must have
     *     "page-header" id
     */
    protected void setHeaderPanel(Component component) {
        if (!HEADER_PANEL.equals(component.getId()))
            throw new IllegalArgumentException("The header panel component must have 'headerPanel' id");
        remove(HEADER_PANEL);
        add(component);
    }

    /** Returns the application instance. */
    protected GeoServerApplication getGeoServerApplication() {
        return (GeoServerApplication) getApplication();
    }

    @Override
    public GeoServerSession getSession() {
        return (GeoServerSession) super.getSession();
    }

    /** Convenience method for pages to get access to the geoserver configuration. */
    protected GeoServer getGeoServer() {
        return getGeoServerApplication().getGeoServer();
    }

    /** Convenience method for pages to get access to the catalog. */
    protected Catalog getCatalog() {
        return getGeoServerApplication().getCatalog();
    }

    /** Splits up the pages by category, turning the list into a map keyed by category */
    private <T extends GeoServerBasePage> Map<Category, List<MenuPageInfo<T>>> splitByCategory(
            List<MenuPageInfo<T>> pages) {
        Collections.sort(pages);
        Map<Category, List<MenuPageInfo<T>>> map = new HashMap<>();

        for (MenuPageInfo<T> page : pages) {
            Category cat = page.getCategory();

            if (!map.containsKey(cat)) map.put(cat, new ArrayList<>());

            map.get(cat).add(page);
        }

        return map;
    }

    /** Filters a set of component descriptors based on the current authenticated user. */
    protected <T extends ComponentInfo> List<T> filterByAuth(List<T> list) {
        Authentication user = getSession().getAuthentication();
        List<T> result = new ArrayList<>();
        for (T component : list) {
            if (component.getAuthorizer() == null) {
                continue;
            }

            final Class<?> clazz = component.getComponentClass();
            if (!component.getAuthorizer().isAccessAllowed(clazz, user)) continue;
            result.add(component);
        }
        return result;
    }

    /**
     * Returns the id for the component used as a veil for the whole page while Wicket is processing an ajax request, so
     * it is impossible to trigger the same ajax action multiple times (think of saving/deleting a resource, etc)
     *
     * @see IAjaxIndicatorAware#getAjaxIndicatorMarkupId()
     */
    @Override
    public String getAjaxIndicatorMarkupId() {
        return "ajaxFeedback";
    }

    /**
     * Sets the return page to navigate to when this page is done its task.
     *
     * @see #doReturn()
     */
    public GeoServerBasePage setReturnPage(Page returnPage) {
        this.returnPage = returnPage;
        return this;
    }

    /**
     * Sets the return page class to navigate to when this page is done its task.
     *
     * @see #doReturn()
     */
    public GeoServerBasePage setReturnPage(Class<? extends Page> returnPageClass) {
        this.returnPageClass = returnPageClass;
        return this;
    }

    /**
     * Returns from the page by navigating to one of {@link #returnPage} or {@link #returnPageClass}, processed in that
     * order.
     *
     * <p>This method should be called by pages that must return after doing some task on a form submit such as a save
     * or a cancel. If no return page has been set via {@link #setReturnPage(Page)} or {@link #setReturnPage(Class)}
     * then {@link GeoServerHomePage} is used.
     */
    protected void doReturn() {
        doReturn(null);
    }

    /**
     * Returns from the page by navigating to one of {@link #returnPage} or {@link #returnPageClass}, processed in that
     * order.
     *
     * <p>This method accepts a parameter to use as a default in cases where {@link #returnPage} and
     * {@link #returnPageClass} are not set and a default other than {@link GeoServerHomePage} should be used.
     *
     * <p>This method should be called by pages that must return after doing some task on a form submit such as a save
     * or a cancel. If no return page has been set via {@link #setReturnPage(Page)} or {@link #setResponsePage(Class)}
     * then {@link GeoServerHomePage} is used.
     */
    protected void doReturn(Class<? extends Page> defaultPageClass) {
        if (returnPage != null) {
            setResponsePage(returnPage);
            return;
        }
        if (returnPageClass != null) {
            setResponsePage(returnPageClass);
            return;
        }

        defaultPageClass = defaultPageClass != null ? defaultPageClass : GeoServerHomePage.class;
        setResponsePage(defaultPageClass);
    }

    public void addFeedbackPanels(AjaxRequestTarget target) {
        target.add(topFeedbackPanel);
        target.add(bottomFeedbackPanel);
    }
}
