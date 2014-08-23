'use strict';

/* Controllers */

var userSignup = angular.module('userSignup', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);

userSignup.controller('UserSignupCtrl', function ($scope) {
  $scope.firstName = "";
  $scope.lastName = "";
  $scope.email = "";
  $scope.initials = "";
  $scope.passA = "";
  $scope.passB = "";
});

