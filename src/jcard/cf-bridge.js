/*
 * CassetteFlow bridge for the bundled J-Card Template.
 *
 * This file is part of CassetteFlow, NOT part of the vendored J-Card Template
 * (see VENDOR.md). It is injected by CassetteFlowServer before </body>.
 *
 * The vendored app has no URL parameter for card data, so the bridge fills the card
 * in through the app's own module API - the same populate/name/refresh sequence the
 * app runs after reading a .jcard.json - so a card opened from a link renders
 * without a dialog getting in the way. If that API ever changes, it falls back to
 * handing the app a dropped file, which is the app's normal load path.
 *
 * Data source, in order of preference:
 *   1. window.CF_JCARD_DATA           (injected by the server for ?tape=...)
 *   2. ?data=<base64url json>         (used by the library app)
 *   3. nothing                        (the app starts with its own preset card)
 */
(function () {
  'use strict';

  var params = new URLSearchParams(window.location.search);

  /** Decodes the ?data= base64url payload, returning null on any fault. */
  function readParamData() {
    var raw = params.get('data');
    if (!raw) return null;

    try {
      var base64 = raw.replace(/-/g, '+').replace(/_/g, '/');
      var pad = base64.length % 4;
      if (pad) base64 += new Array(5 - pad).join('=');

      var binary = window.atob(base64);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);

      return JSON.parse(new TextDecoder('utf-8').decode(bytes));
    } catch (error) {
      window.console.warn('[CassetteFlow] Unable to decode the J-Card data', error);
      return null;
    }
  }

  /** Returns the card data for this page, or null when there is none. */
  function readData() {
    if (window.CF_JCARD_DATA) return window.CF_JCARD_DATA;
    return readParamData();
  }

  /** The card's file name, so the app's own title and download name are useful. */
  function fileNameFor(data) {
    var name = data.cfTapeId || data.titleUpper || 'tape';
    return String(name).replace(/[^A-Za-z0-9._ -]+/g, '_').trim() + '.jcard.json';
  }

  /** Waits for the given test to pass, up to the timeout. */
  function waitFor(test, timeoutMs) {
    return new Promise(function (resolve) {
      var deadline = Date.now() + timeoutMs;

      (function poll() {
        var result = null;
        try {
          result = test();
        } catch (error) {
          result = null;
        }

        if (result) return resolve(result);
        if (Date.now() > deadline) return resolve(null);
        window.setTimeout(poll, 100);
      })();
    });
  }

  /** A card file for the app's own file field. */
  function fileFor(data) {
    return new File([JSON.stringify(data)], fileNameFor(data), { type: 'application/json' });
  }

  /** Adds our card to the page the way the app accepts a dragged in file. */
  function dropInto(root, data) {
    var transfer = new window.DataTransfer();
    transfer.items.add(fileFor(data));

    if (!transfer.files.length) return false;

    root.dispatchEvent(new window.DragEvent('drop', {
      bubbles: true,
      cancelable: true,
      dataTransfer: transfer
    }));

    return true;
  }

  /**
   * Fill the app's own form entries, then refresh the card. This is what the app
   * itself does after reading a card file (populateDataSaves, set the save name,
   * updateData) without the file picker's "discard your changes?" prompt, so a card
   * opened from a link never blocks on a dialog.
   */
  function loadDirect(app, data) {
    try {
      try {
        // cosmetic: shows the card's file name in the app's file field
        var transfer = new window.DataTransfer();
        transfer.items.add(fileFor(data));
        app.getSource().element.files = transfer.files;
      } catch (error) {
        // the field is optional, so a refusal here is not a failure
      }

      app.populateDataSaves(data);

      var name = typeof app.getSaveEntry === 'function' ? app.getSaveEntry('name') : null;
      if (name && name.element) {
        name.value = fileNameFor(data).replace(/\.[^.]+$/, '');
        name.element.dispatchEvent(new window.Event('input', { bubbles: true }));
      }

      app.updateData();
      return true;
    } catch (error) {
      window.console.warn('[CassetteFlow] The direct J-Card load failed', error);
      return false;
    }
  }

  /** True once the card shown by the app is ours. */
  function cardIsOurs(data) {
    var input = document.getElementById('input-title-upper');
    if (!input) return false;
    if (!data.titleUpper) return true;

    return input.value.trim().toLowerCase() === String(data.titleUpper).trim().toLowerCase();
  }

  /** Clears the app's "unsaved changes" flag for a card that came from CassetteFlow. */
  function clearModifiedFlag() {
    import('./res/scripts/common/application-functions.mjs')
      .then(function (common) {
        if (typeof common.setModified === 'function') common.setModified(false);
      })
      .catch(function () {
        // not fatal: the user would just get the app's usual unload prompt
      });
  }

  function apply(data) {
    if (!data) return;

    // the app's own module: it owns the card entries and the render refresh
    import('./res/scripts/application-functions.mjs').then(function (app) {
      waitFor(function () {
        return document.getElementById('input-title-upper') ? true : null;
      }, 5000).then(function (ready) {
        if (!ready) {
          window.console.warn('[CassetteFlow] The J-Card app did not start in time');
          return;
        }

        if (!loadDirect(app, data)) {
          try {
            dropInto(app.getRoot().element, data);
          } catch (error) {
            window.console.error('[CassetteFlow] Unable to hand the J-Card to the app', error);
          }
        }

        waitFor(function () {
          return cardIsOurs(data) ? true : null;
        }, 3000).then(function (applied) {
          window.CF_JCARD_LOADED = applied === true;

          if (applied) {
            // the card is stored by CassetteFlow, so there is nothing to lose here
            clearModifiedFlag();
          } else {
            window.console.warn('[CassetteFlow] The J-Card data was not applied');
          }

          var label = data.cfTapeId || data.titleUpper;
          if (label) {
            window.setTimeout(function () { document.title = label + ' \u2014 CassetteFlow'; }, 250);
          }
        });
      });
    }).catch(function (error) {
      window.console.error('[CassetteFlow] Unable to load the J-Card app', error);
    });
  }

  if (document.readyState === 'complete') apply(readData());
  else window.addEventListener('load', function () { apply(readData()); });
})();
