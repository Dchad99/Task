/*
 * Listing page client.
 *
 * Rules this file follows:
 *  - the browser never holds more than one page of data;
 *  - search input is debounced, and stale in-flight requests are aborted, so a
 *    slow earlier response can never overwrite a newer one;
 *  - changing any filter resets to page 0;
 *  - every load has an explicit loading, error and empty state;
 *  - deleting the last row on a non-first page steps back a page instead of
 *    showing an empty table.
 */
(function () {
    'use strict';

    var API_ITEMS = '/api/items';
    var API_CATEGORIES = '/api/categories';
    var DEBOUNCE_MS = 300;

    var state = { q: '', category: '', sort: 'createdAt,desc', page: 0, size: 25 };

    var requestSequence = 0;
    var inFlightController = null;

    var el = {
        q: document.getElementById('filter-q'),
        category: document.getElementById('filter-category'),
        sort: document.getElementById('filter-sort'),
        size: document.getElementById('filter-size'),
        body: document.getElementById('results-body'),
        table: document.getElementById('results-table'),
        empty: document.getElementById('empty'),
        listError: document.getElementById('list-error'),
        loading: document.getElementById('loading'),
        summary: document.getElementById('summary'),
        prev: document.getElementById('prev'),
        next: document.getElementById('next'),
        pageIndicator: document.getElementById('page-indicator'),
        addForm: document.getElementById('add-form'),
        addName: document.getElementById('add-name'),
        addCategory: document.getElementById('add-category'),
        addDescription: document.getElementById('add-description'),
        addPrice: document.getElementById('add-price'),
        addError: document.getElementById('add-error')
    };

    function debounce(fn, wait) {
        var timer = null;
        return function () {
            var args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(null, args); }, wait);
        };
    }

    function buildListUrl() {
        var params = new URLSearchParams();
        if (state.q) params.set('q', state.q);
        if (state.category) params.set('category', state.category);
        params.set('page', String(state.page));
        params.set('size', String(state.size));
        params.set('sort', state.sort);
        return API_ITEMS + '?' + params.toString();
    }

    function readErrorMessage(response) {
        return response.json().then(function (body) {
            if (body && body.fieldErrors) {
                return Object.keys(body.fieldErrors)
                    .map(function (f) { return f + ': ' + body.fieldErrors[f]; }).join('; ');
            }
            return (body && body.message) || ('Request failed with status ' + response.status + '.');
        }).catch(function () {
            return 'Request failed with status ' + response.status + '.';
        });
    }

    function show(element, visible) { element.hidden = !visible; }

    function formatPrice(value) {
        return value === null || value === undefined ? '—' : Number(value).toFixed(2);
    }

    function formatDate(isoString) {
        var date = new Date(isoString);
        return isNaN(date.getTime()) ? '—' : date.toLocaleString();
    }

    function cell(text) {
        var td = document.createElement('td');
        td.textContent = text;
        return td;
    }

    function renderRows(items) {
        var fragment = document.createDocumentFragment();
        items.forEach(function (item) {
            var row = document.createElement('tr');
            row.appendChild(cell(item.name));
            row.appendChild(cell(item.category));

            var description = cell(item.description || '—');
            description.className = 'description';
            row.appendChild(description);

            var price = cell(formatPrice(item.price));
            price.className = 'numeric';
            row.appendChild(price);

            row.appendChild(cell(formatDate(item.createdAt)));

            var actions = document.createElement('td');
            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'button button--danger';
            remove.textContent = 'Delete';
            remove.addEventListener('click', function () { deleteItem(item.id, remove); });
            actions.appendChild(remove);
            row.appendChild(actions);

            fragment.appendChild(row);
        });
        el.body.replaceChildren(fragment);
    }

    // Note: the API's own field is also called "page" (a nested metadata object),
    // so the parameter here is named pageResponse to keep pageResponse.page.number
    // readable rather than colliding with a same-named outer variable.
    function renderSummary(pageResponse) {
        var meta = pageResponse.page;
        if (meta.totalElements === 0) { el.summary.textContent = '0 items'; return; }
        var first = meta.number * meta.size + 1;
        var last = first + pageResponse.content.length - 1;
        el.summary.textContent = 'Showing ' + first + '–' + last + ' of ' + meta.totalElements + ' items';
    }

    function renderPagination(pageResponse) {
        var meta = pageResponse.page;
        var isLastPage = meta.number >= meta.totalPages - 1;
        el.prev.disabled = meta.number <= 0;
        el.next.disabled = isLastPage;
        el.pageIndicator.textContent = 'Page ' + (meta.number + 1) + ' of ' + Math.max(meta.totalPages, 1);
    }

    function load() {
        var mySequence = ++requestSequence;

        if (inFlightController) inFlightController.abort();
        inFlightController = new AbortController();

        show(el.loading, true);
        show(el.listError, false);

        fetch(buildListUrl(), { signal: inFlightController.signal, headers: { Accept: 'application/json' } })
            .then(function (response) {
                if (!response.ok) {
                    return readErrorMessage(response).then(function (m) { throw new Error(m); });
                }
                return response.json();
            })
            .then(function (pageResponse) {
                if (mySequence !== requestSequence) return; // superseded by a newer request
                renderRows(pageResponse.content);
                renderSummary(pageResponse);
                renderPagination(pageResponse);
                show(el.table, pageResponse.content.length > 0);
                show(el.empty, pageResponse.content.length === 0);
                show(el.loading, false);
            })
            .catch(function (error) {
                if (error.name === 'AbortError' || mySequence !== requestSequence) return;
                show(el.loading, false);
                show(el.table, false);
                show(el.empty, false);
                el.listError.textContent = error.message;
                show(el.listError, true);
                el.summary.textContent = '';
            });
    }

    function resetToFirstPageAndLoad() { state.page = 0; load(); }

    function deleteItem(id, button) {
        button.disabled = true;
        show(el.listError, false);

        fetch(API_ITEMS + '/' + id, { method: 'DELETE' })
            .then(function (response) {
                if (response.status === 404) {
                    throw new Error('That item no longer exists. The list has been refreshed.');
                }
                if (!response.ok) {
                    return readErrorMessage(response).then(function (m) { throw new Error(m); });
                }
                var wasLastRowOnPage = el.body.children.length === 1;
                if (wasLastRowOnPage && state.page > 0) state.page -= 1;
                load();
            })
            .catch(function (error) {
                button.disabled = false;
                el.listError.textContent = error.message;
                show(el.listError, true);
                load();
            });
    }

    function submitNewItem(event) {
        event.preventDefault();
        show(el.addError, false);

        var priceValue = el.addPrice.value.trim();
        var descriptionValue = el.addDescription.value.trim();

        var payload = {
            name: el.addName.value.trim(),
            category: el.addCategory.value,
            description: descriptionValue === '' ? null : descriptionValue,
            price: priceValue === '' ? null : Number(priceValue)
        };

        fetch(API_ITEMS, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
            .then(function (response) {
                if (response.status !== 201) {
                    return readErrorMessage(response).then(function (m) { throw new Error(m); });
                }
                el.addForm.reset();
                resetToFirstPageAndLoad();
            })
            .catch(function (error) {
                el.addError.textContent = error.message;
                show(el.addError, true);
            });
    }

    function loadCategories() {
        return fetch(API_CATEGORIES)
            .then(function (response) { return response.json(); })
            .then(function (categories) {
                categories.forEach(function (category) {
                    el.category.appendChild(new Option(category, category));
                    el.addCategory.appendChild(new Option(category, category));
                });
            })
            .catch(function () {
                el.addError.textContent = 'Could not load categories.';
                show(el.addError, true);
            });
    }

    function wireEvents() {
        el.q.addEventListener('input', debounce(function () {
            state.q = el.q.value.trim();
            resetToFirstPageAndLoad();
        }, DEBOUNCE_MS));

        el.category.addEventListener('change', function () {
            state.category = el.category.value;
            resetToFirstPageAndLoad();
        });

        el.sort.addEventListener('change', function () {
            state.sort = el.sort.value;
            resetToFirstPageAndLoad();
        });

        el.size.addEventListener('change', function () {
            state.size = Number(el.size.value);
            resetToFirstPageAndLoad();
        });

        el.prev.addEventListener('click', function () {
            if (state.page > 0) { state.page -= 1; load(); }
        });

        el.next.addEventListener('click', function () { state.page += 1; load(); });

        el.addForm.addEventListener('submit', submitNewItem);
    }

    wireEvents();
    loadCategories().then(load);
})();
