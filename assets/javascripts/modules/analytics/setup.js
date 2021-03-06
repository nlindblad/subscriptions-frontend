define(['modules/analytics/ga',
        'modules/analytics/omniture',
        'modules/analytics/remarketing',
        'modules/analytics/krux',
        'modules/analytics/affectv',
        'modules/analytics/snowplow'
], function (ga,
             omniture,
             remarketing,
             krux,
             affectv,
             snowplow) {
    'use strict';

    function init() {
        snowplow.init();
        ga.init();
        omniture.init();

        if (!window.guardian.isDev) {
            remarketing.init();
            krux.init();
            affectv.init();
        }
    }

    return {
        init: init
    };
});
