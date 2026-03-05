(function() {
    document.addEventListener("DOMContentLoaded", function() {

        // Theme toggle: light theme by default, persist choice in localStorage
        (function initThemeToggle() {
            var STORAGE_KEY = 'gs-theme';
            var html = document.documentElement;
            var checkbox = document.getElementById('gs-switch');

            function applyTheme(isDark) {
                html.setAttribute('data-gs-theme', isDark ? 'dark' : 'light');
                if (checkbox) checkbox.checked = isDark;
            }

            var saved = localStorage.getItem(STORAGE_KEY);
            var isDark = saved === 'dark';
            applyTheme(isDark);

            if (checkbox) {
                checkbox.addEventListener('change', function() {
                    isDark = checkbox.checked;
                    localStorage.setItem(STORAGE_KEY, isDark ? 'dark' : 'light');
                    applyTheme(isDark);
                });
            }
        })();

        // Mobile navigation: hamburger toggles overlay panel, ESC/backdrop/close button close it
        function initializeNavigationMenu() {
            const hamburger = document.querySelector('#navigation-menu');
            const mainNavigation = document.querySelector('#main-navigation');
            const navigationClose = document.querySelector('#navigation-close');
            const backdrop = document.querySelector('#header-backdrop');

            function openNavigation() {
                if (mainNavigation) mainNavigation.classList.add('is-open');
                if (backdrop) {
                    backdrop.classList.add('is-visible');
                    backdrop.setAttribute('aria-hidden', 'false');
                }
                if (hamburger) hamburger.setAttribute('aria-expanded', 'true');
                document.body.style.overflow = 'hidden';
            }

            function closeNavigation() {
                if (mainNavigation) mainNavigation.classList.remove('is-open');
                if (backdrop) {
                    backdrop.classList.remove('is-visible');
                    backdrop.setAttribute('aria-hidden', 'true');
                }
                if (hamburger) hamburger.setAttribute('aria-expanded', 'false');
                document.body.style.overflow = '';
            }

            function isNavigationOpen() {
                return mainNavigation && mainNavigation.classList.contains('is-open');
            }

            if (hamburger) {
                hamburger.addEventListener('click', function() {
                    isNavigationOpen() ? closeNavigation() : openNavigation();
                });
            }
            if (navigationClose) {
                navigationClose.addEventListener('click', closeNavigation);
            }
            if (backdrop) {
                backdrop.addEventListener('click', closeNavigation);
            }
            document.addEventListener('keydown', function(e) {
                if (e.key === 'Escape' && isNavigationOpen()) closeNavigation();
            });

            window.addEventListener('resize', function() {
                if (window.innerWidth > 768 && isNavigationOpen()) closeNavigation();
            });
        }
        initializeNavigationMenu();

        // Sidebar "New" menu: toggle on button, close on outside click or ESC
        function initializeSidebarNewMenu() {
            const toggleButton = document.getElementById('gs-sidebar-new-toggle');
            const menu = document.getElementById('gs-sidebar-new-menu');
            if (!toggleButton || !menu) return;

            function closeMenu() {
                menu.setAttribute('hidden', 'hidden');
                toggleButton.setAttribute('aria-expanded', 'false');
            }

            function openMenu() {
                menu.removeAttribute('hidden');
                toggleButton.setAttribute('aria-expanded', 'true');
            }

            function isMenuOpen() {
                return !menu.hasAttribute('hidden');
            }

            toggleButton.addEventListener('click', function() {
                if (isMenuOpen()) {
                    closeMenu();
                    return;
                }
                openMenu();
            });

            document.addEventListener('click', function(e) {
                if (!isMenuOpen()) return;
                if (!toggleButton.contains(e.target) && !menu.contains(e.target)) {
                    closeMenu();
                }
            });

            document.addEventListener('keydown', function(e) {
                if (e.key === 'Escape' && isMenuOpen()) {
                    closeMenu();
                    toggleButton.focus();
                }
            });
        }
        initializeSidebarNewMenu();

        // Sidebar workspace search clear button behavior
        function initializeSidebarWorkspaceSearchClear() {
            const searchForm = document.querySelector('.gs-sidebar-search-box');
            if (!searchForm) return;

            const searchInput = searchForm.querySelector('.gs-sidebar-search-input');
            const clearButton = searchForm.querySelector('.gs-sidebar-search-clear');
            if (!searchInput || !clearButton) return;

            function updateClearVisibility() {
                const hasText = !!(searchInput.value && searchInput.value.trim().length);
                clearButton.classList.toggle('is-visible', hasText);
                if (hasText) {
                    clearButton.removeAttribute('hidden');
                } else {
                    clearButton.setAttribute('hidden', 'hidden');
                }
            }

            updateClearVisibility();
            searchInput.addEventListener('input', updateClearVisibility);

            clearButton.addEventListener('click', function(e) {
                e.preventDefault();
                searchInput.value = '';
                updateClearVisibility();
                searchInput.focus();
                if (typeof searchForm.requestSubmit === 'function') {
                    searchForm.requestSubmit();
                } else {
                    searchForm.submit();
                }
            });
        }
        initializeSidebarWorkspaceSearchClear();

        // Sidebar workspace pagination for workspace
        function initializeSidebarWorkspacePagination() {
            const workspaceList = document.getElementById('gs-workspaces-list');
            const pagination = document.querySelector('.gs-sidebar-pagination');
            if (!workspaceList || !pagination) return;

            const buttons = pagination.querySelectorAll('.gs-sidebar-pagination-button');
            const pageInfo = pagination.querySelector('.gs-sidebar-pagination-info');
            if (!buttons.length || buttons.length < 2 || !pageInfo) return;

            const prevButton = buttons[0];
            const nextButton = buttons[1];
            const pageSize = 5;
            const items = Array.prototype.slice.call(
                workspaceList.querySelectorAll(':scope > .gs-sidebar-item')
            );
            if (!items.length) {
                pageInfo.textContent = '0/0';
                prevButton.disabled = true;
                nextButton.disabled = true;
                return;
            }

            const totalPages = Math.ceil(items.length / pageSize);
            let currentPage = 1;

            function renderPage() {
                const start = (currentPage - 1) * pageSize;
                const end = start + pageSize;

                items.forEach(function(item, index) {
                    const visible = index >= start && index < end;
                    if (visible) {
                        item.style.display = '';
                        item.removeAttribute('hidden');
                    } else {
                        item.style.display = 'none';
                        item.setAttribute('hidden', 'hidden');
                    }
                });

                pageInfo.textContent = currentPage + '/' + totalPages;
                prevButton.disabled = currentPage <= 1;
                nextButton.disabled = currentPage >= totalPages;
            }

            prevButton.addEventListener('click', function() {
                if (currentPage <= 1) return;
                currentPage--;
                renderPage();
            });

            nextButton.addEventListener('click', function() {
                if (currentPage >= totalPages) return;
                currentPage++;
                renderPage();
            });

            renderPage();
        }
        initializeSidebarWorkspacePagination();

        // Workspace layer pagination: paginate layers independently per workspace
        function initializeSidebarWorkspaceLayerPagination() {
            const layerLists = document.querySelectorAll('.gs-sidebar-workspace-layers');
            if (!layerLists.length) return;

            const pageSize = 5;

            layerLists.forEach(function(layerList) {
                const workspaceItem = layerList.closest('.gs-sidebar-item');
                if (!workspaceItem) return;

                const pagination = workspaceItem.querySelector('.gs-sidebar-layers-pagination');
                if (!pagination) return;

                const buttons = pagination.querySelectorAll('.gs-sidebar-pagination-button');
                const pageInfo = pagination.querySelector('.gs-sidebar-pagination-info');
                if (buttons.length < 2 || !pageInfo) return;

                const prevButton = buttons[0];
                const nextButton = buttons[1];
                const items = Array.prototype.slice.call(
                    layerList.querySelectorAll(':scope > li:not(.gs-sidebar-layers-pagination-item)')
                );

                if (!items.length) {
                    pageInfo.textContent = '0/0';
                    prevButton.disabled = true;
                    nextButton.disabled = true;
                    return;
                }

                const totalPages = Math.ceil(items.length / pageSize);
                let currentPage = 1;

                function renderPage() {
                    const start = (currentPage - 1) * pageSize;
                    const end = start + pageSize;

                    items.forEach(function(item, index) {
                        const visible = index >= start && index < end;
                        if (visible) {
                            item.style.display = '';
                            item.removeAttribute('hidden');
                        } else {
                            item.style.display = 'none';
                            item.setAttribute('hidden', 'hidden');
                        }
                    });

                    pageInfo.textContent = currentPage + '/' + totalPages;
                    prevButton.disabled = currentPage <= 1;
                    nextButton.disabled = currentPage >= totalPages;
                }

                prevButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    if (currentPage <= 1) return;
                    currentPage--;
                    renderPage();
                });

                nextButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    if (currentPage >= totalPages) return;
                    currentPage++;
                    renderPage();
                });

                renderPage();
            });
        }
        initializeSidebarWorkspaceLayerPagination();

        // Sidebar tree toggles: right chevron when closed, down when open
        function initializeSidebarWorkspaceTree() {
            const toggles = document.querySelectorAll('.gs-sidebar-tree-toggle');
            if (!toggles.length) return;

            toggles.forEach(function(toggle) {
                const controlsId = toggle.getAttribute('aria-controls');
                if (!controlsId) return;

                const list = document.getElementById(controlsId);
                if (!list) {
                    toggle.setAttribute('aria-expanded', 'false');
                    return;
                }

                function openList() {
                    list.removeAttribute('hidden');
                    toggle.setAttribute('aria-expanded', 'true');
                }

                function closeList() {
                    list.setAttribute('hidden', 'hidden');
                    toggle.setAttribute('aria-expanded', 'false');
                }

                function isOpen() {
                    return !list.hasAttribute('hidden');
                }

                toggle.setAttribute('aria-expanded', isOpen() ? 'true' : 'false');

                toggle.addEventListener('click', function(e) {
                    if (e.target && e.target.closest && e.target.closest('.gs-sidebar-workspace-link')) {
                        return;
                    }
                    isOpen() ? closeList() : openList();
                });

                toggle.addEventListener('keydown', function(e) {
                    if (e.key === 'ArrowRight' && !isOpen()) {
                        e.preventDefault();
                        openList();
                    } else if (e.key === 'ArrowLeft' && isOpen()) {
                        e.preventDefault();
                        closeList();
                    } else if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        isOpen() ? closeList() : openList();
                    }
                });
            });
        }
        initializeSidebarWorkspaceTree();

        // User dropdown: click avatar to toggle, close on outside click or ESC
        function initializeUserDropdown() {
            const trigger = document.querySelector('#user-avatar-trigger');
            const panel = document.querySelector('#user-dropdown-panel');
            if (!trigger || !panel) return;

            function open() {
                panel.removeAttribute('hidden');
                trigger.setAttribute('aria-expanded', 'true');
            }
            function close() {
                panel.setAttribute('hidden', '');
                trigger.setAttribute('aria-expanded', 'false');
            }
            function toggle() {
                if (panel.hasAttribute('hidden')) open(); else close();
            }

            trigger.addEventListener('click', function(e) {
                e.preventDefault();
                toggle();
            });
            trigger.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    toggle();
                }
            });
            document.addEventListener('click', function(e) {
                if (!trigger.contains(e.target) && !panel.contains(e.target)) close();
            });
            document.addEventListener('keydown', function(e) {
                if (e.key === 'Escape') close();
            });
        }
        initializeUserDropdown();

        // Initialize avatar initials from username text in the dropdown
        function initializeUserInitials() {
            var avatarInitials = document.querySelector('.gs-user-avatar .gs-user-initials');
            if (!avatarInitials) return;

            var usernameSpan = document.querySelector('.gs-user-dropdown-panel .username span');
            var username = usernameSpan && usernameSpan.textContent
                ? usernameSpan.textContent.trim()
                : '';

            if (username) {
                avatarInitials.textContent = username.charAt(0).toUpperCase();
            } else {
                // No username available: clear initials so CSS can show anonymous icon
                avatarInitials.textContent = '';
            }
        }
        initializeUserInitials();
    });
})();

