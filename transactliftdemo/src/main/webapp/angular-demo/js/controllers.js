'use strict';

/* Controllers */

var demoApp = angular.module('demoApp', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);

demoApp.controller('DemoCtrl', function ($scope) {
  $scope.entries = [];
  $scope.inOutSetting = 'either';
  $scope.time = 0;

  $scope.remove = function(deleteGUID){
    liftAjax.lift_ajaxHandler(deleteGUID + '=true', null, null, null)
  };

});

var timesheetView = angular.module('timesheetView', ['ui.bootstrap', 'angularBoxes', 'ngAnimate', 'angularMoment']);

timesheetView.controller('TimesheetController', function ($scope, $modal, $log) {
  
  $scope.action = function(guid){
    console.log("Calling guid " + guid)
    liftAjax.lift_ajaxHandler(guid + '=true', null, null, null)
  };

  $scope.actionAndData = function(guid, data){
    var json = JSON.stringify(data)
    console.log("Calling guid " + guid + " with json " + json)
    liftAjax.lift_ajaxHandler(guid + '=' + json, null, null, null)
  };
  
  $scope.openLateInOut = function () {
    var modalInstance = $modal.open({
      templateUrl: 'TimesheetLateInOutModalContent.html',
      controller: ModalInstanceCtrl,
      resolve: {
        time: function () {
          return new Date();
        }
      }
    });

    modalInstance.result.then(function (entry) {
      $scope.actionAndData($scope.late, entry)
    }, function () {});
  };
    
  $scope.$watchCollection('last7Days', function(n, o) {
    $scope.last7DaysCount = n.length;
    $log.log(n.length)
  });
  
});

var ModalInstanceCtrl = function ($scope, $modalInstance, time) {
  
  $scope.entry={'in': false, 'time': time};

  $scope.lateIn = function () {
    $scope.entry.in = true;
    $modalInstance.close($scope.entry);
  };

  $scope.lateOut = function () {
    $scope.entry.in = false;
    $modalInstance.close($scope.entry);
  };

  $scope.cancel = function () {
    $modalInstance.dismiss('cancel');
  };
};
