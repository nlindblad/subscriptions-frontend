define([
    'modules/forms/toggleError',
    'utils/ajax',
    'modules/checkout/formElements',
    'modules/checkout/countryChoice',
    'bean',
    '$'
], function (toggleError, ajax, formElements, countryChoice, bean, $) {
    'use strict';

    var getValidity = function () {

        var country       = countryChoice.getCurrentCountryOption().value,
            $inputBox     = formElements.$PROMO_CODE,
            $promoCodeMsg = formElements.$PROMOCODE_MSG,
            $promoCodeImg = formElements.$PROMOCODE_IMG,
            $promoCodeBtn = formElements.$PROMOCODE_BTN,
            lookupUrl     = $inputBox.data('lookup-url'),
            promoCode     = $inputBox.val().trim(),
            ratePlanId    = formElements.getRatePlanId();

        if (promoCode == '') {
            return;
        }

        ajax({
            type: 'json',
            method: 'GET',
            url: lookupUrl,
            data: {
                promoCode: promoCode,
                productRatePlanId: ratePlanId,
                country: country
            }
        }).then(function (a) {
            toggleError($inputBox, false);
            $promoCodeMsg.text(a.promotion.description);
            $promoCodeImg.attr('src', a.thumbnailUrl);
        }).catch(function (a) {
            //I think that I've made some mistake with the html which is causing this toggleError to never really set an error
            toggleError(formElements.$PROMO_CODE, true);
            // Content of error codes are not parsed by ajax/reqwest.
            var b = JSON.parse(a.response);
            console.log(b);
            $promoCodeMsg.text(b.message);
            $promoCodeImg.attr('src', a.thumbnailUrl);
        })
    };

    return {
        init: function () {
            getValidity();
            bean.on(formElements.$COUNTRY_SELECT[0], 'change', getValidity);
            bean.on($promoCodeBtn[0], 'click', getValidity);

        }
    };
});
