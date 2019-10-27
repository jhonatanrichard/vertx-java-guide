'use strict';

function generateUUID() {
  var d = new Date().getTime();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    var r = (d + Math.random() * 16) % 16 | 0;
    d = Math.floor(d / 16);
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

angular.module("wikiApp", [])
  .controller("WikiController", ["$scope", "$http", "$timeout", function ($scope, $http, $timeout) {

    var DEFAULT_PAGENAME = "Example page";
    var DEFAULT_MARKDOWN = "# Example page\n\nSome text _here_.\n";

    $scope.newPage = function () {
      $scope.pageId = undefined;
      $scope.pageName = DEFAULT_PAGENAME;
      $scope.pageMarkdown = DEFAULT_MARKDOWN;
    };

    $scope.reload = function () {
      $http.get("/api/pages").then(function (response) {
        $scope.pages = response.data.pages;
      });
    };

    $scope.pageExists = function() {
      return $scope.pageId !== undefined;
    };

    $scope.load = function (id) {
      $scope.pageModified = false;
      $http.get("/api/pages/" + id).then(function(response) {
        var page = response.data.page;
        $scope.pageId = page.id;
        $scope.pageName = page.name;
        $scope.pageMarkdown = page.markdown;
        $scope.updateRendering(page.html);
      });
    };

    $scope.updateRendering = function(html) {
      document.getElementById("rendering").innerHTML = html;
    };

    $scope.save = function () {
      var payload;
      if ($scope.pageId === undefined) {
        payload = {
          "name": $scope.pageName,
          "markdown": $scope.pageMarkdown
        };
        $http.post("/api/pages", payload).then(function(ok) {
          $scope.reload();
          $scope.success("Page created");
          var guessMaxId = _.maxBy($scope.pages, function(page) { return page.id; });
          $scope.load(guessMaxId.id || 0);
        }, function(err) {
          $scope.error(err.data.error);
        });
      } else {
        var payload = {
          "client": clientUuid,
          "markdown": $scope.pageMarkdown
        };
        $http.put("/api/pages/" + $scope.pageId, payload).then(function(ok) {
          $scope.success("Page saved");
        }, function(err) {
          $scope.error(err.data.error);
        });
      }
    };

    $scope.delete = function() {
      $http.delete("/api/pages/" + $scope.pageId).then(function(ok) {
        $scope.reload();
        $scope.newPage();
        $scope.success("Page deleted");
      }, function(err) {
        $scope.error(err.data.error);
      });
    };

    $scope.success = function(message) {
      $scope.alertMessage = message;
      var alert = document.getElementById("alertMessage");
      alert.classList.add("alert-success");
      alert.classList.remove("invisible");
      $timeout(function() {
        alert.classList.add("invisible");
        alert.classList.remove("alert-success");
      }, 3000);
    };

    $scope.error = function(message) {
      $scope.alertMessage = message;
      var alert = document.getElementById("alertMessage");
      alert.classList.add("alert-danger");
      alert.classList.remove("invisible");
      $timeout(function() {
        alert.classList.add("invisible");
        alert.classList.remove("alert-danger");
      }, 5000);
    };

    $scope.reload();
    $scope.newPage();

    var markdownRenderingPromise = null;
    $scope.$watch("pageMarkdown", function (text) {
      if (eb.state !== EventBus.OPEN) return;
      if (markdownRenderingPromise !== null) {
        $timeout.cancel(markdownRenderingPromise);
      }
      markdownRenderingPromise = $timeout(function() {
        markdownRenderingPromise = null;
        
        eb.send("app.markdown", text, function (err, reply) { // 1. The reply handler is a function taking two parameters: an error (if any) and the reply object. The reply object content is nested inside the body property.
          if (err === null) {
            $scope.$apply(function () { // 2. Since the event bus client is not managed by AngularJS, $scope.$apply wraps the callback to perform proper scope life-cycle.
              $scope.updateRendering(reply.body); // 3. As we did when working with $http, we invoke updateRendering with the HTML result.
            });
          } else {
            console.warn("Error rendering Markdown content: " + JSON.stringify(err));
          }
        });

      }, 300);
    });


    var eb = new EventBus(window.location.protocol + "//" + window.location.host + "/eventbus");
    var clientUuid = generateUUID(); // 1. We do not want to print the warning if we modified the content ourselves so we need a client identifier.
    eb.onopen = function () {
      eb.registerHandler("page.saved", function (error, message) { // 2. The callback will be invoked when a message is received on the page.saved address.
        if (message.body // 3. Check that the body is not empty.
          && $scope.pageId === message.body.id // 4. Make sure this event is related to the current wiki page.
          && clientUuid !== message.body.client) { // 5. Check that we are not the origin of the changes.
          $scope.$apply(function () { // 6. Since the event bus client is not managed by AngularJS, $scope.$apply wraps the callback to perform proper scope life cycle.
            $scope.pageModified = true; // 7. Set pageModified to true.
          });
        }
      });
    };

  }]);