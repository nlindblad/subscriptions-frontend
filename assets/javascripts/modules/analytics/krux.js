/*global Raven */
define(['modules/analytics/analyticsEnabled'], function(analyticsEnabled) {
    'use strict';

    var KRUX_ID = 'Jglpp88U';

    function init() {
        require(['js!https://cdn.krxd.net/controltag?confid=' + KRUX_ID]).then(null, function(err) {
            Raven.captureException(err);
        });
    }

    return {
        init: analyticsEnabled(init)
    };
});
