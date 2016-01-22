/*global guardian*/
define([
    '$',
    'bean',
    'modules/checkout/countryChoice',
    'modules/checkout/addressFields',
    'modules/checkout/localizationSwitcher',
    'modules/checkout/formElements'
], function ($, bean, countryChoice, addressFields, localizationSwitcher, formElements) {
    'use strict';

    var check = function(domEl) {
        $(domEl).attr('checked', 'checked');
        bean.fire(domEl, 'change');
    };

    var subdivision = function() {
        return $('.js-checkout-subdivision');
    };

    var postcode = function() {
        return $('.js-checkout-postcode');
    };

    var checkPlanInput = function (ratePlanId) {
        formElements.$PLAN_INPUTS.each(function(input) {
            if ($(input).val() === ratePlanId && !$(input).attr('disabled')) {
                check(input);
            }
        });
    };

    var redrawAddressFields = function(model) {
        var newPostcode = addressFields.postcode(
            model.postcodeRules.required,
            model.postcodeRules.label);

        var newSubdivision = addressFields.subdivision(
            model.subdivisionRules.required,
            model.subdivisionRules.label,
            model.subdivisionRules.values);

        newPostcode.input.value = model.postcode;

        $('input', postcode()).replaceWith(newPostcode.input);
        $('label', postcode()).replaceWith(newPostcode.label);

        $('#address-subdivision', subdivision()).replaceWith(newSubdivision.input);
        $('label', subdivision()).replaceWith(newSubdivision.label);
    };

    // Change the payment method to Direct Debit for UK users,
    // Credit Card for international users
    var selectPaymentMethod = function (country) {
        if (country == 'GB') {
            check(formElements.$DIRECT_DEBIT_TYPE[0]);
        } else {
            check(formElements.$CARD_TYPE[0]);
        }
    };

    var switchLocalization = function (model) {
        var currency = model.currency || guardian.currency;
        localizationSwitcher.set(currency, model.country);
    };

    var redraw = function(model) {
        redrawAddressFields(model);
        switchLocalization(model);
        checkPlanInput(model.ratePlanId);
        selectPaymentMethod(model.country);
    };

    var getCurrentState = function() {

        var currentCountryOption = $(countryChoice.getCurrentCountryOption());
        var rules = countryChoice.addressRules();
        return {
            postcode: $('input', postcode()).val(),
            postcodeRules: rules.postcode,
            subdivisionRules: rules.subdivision,
            currency: currentCountryOption.attr('data-currency-choice'),
            country: currentCountryOption.val(),
            ratePlanId: formElements.getRatePlanId()
        };
    };

    var preselectCountry = function() {
        $('option', formElements.$COUNTRY_SELECT).each(function (el) {
            if ($(el).val() === guardian.country) {
                el.selected = true;
            }
        });
    };

    var updateGuardianPageInfo = function(model) {
        var pageInfo = guardian.pageInfo;
        if (pageInfo) {
            pageInfo.billingCountry = model.country;
            pageInfo.billingCurrency = model.currency;
        }
    };

    var refresh = function () {
        var model = getCurrentState();
        redraw(model);
        updateGuardianPageInfo(model);
    };

    var refreshOnChange = function(el) {
        bean.on(el[0], 'change', function() {
            refresh();
        });
    };

    var init = function() {
        if (formElements.$CHECKOUT_FORM.length) {
            preselectCountry();
            refreshOnChange(formElements.$COUNTRY_SELECT);
            refresh();
        }
    };

    return {
        init: init
    };
});
