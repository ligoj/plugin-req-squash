define(function () {
	var current = {

		/**
		 * Render Sqush TM project name.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:req:squash:project');
		},

		/**
		 * Render Build Jenkins data.
		 */
		renderFeatures: function (subscription) {
			var result = current.$super('renderServicelink')('home', 'rest/service/req/squash/redirect/' + subscription.id, 'service:req:squash:project', null, ' target="_blank"');
			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:req:help');
			return result;
		},

		/**
		 * Render SquashTM details : name and display name.
		 */
		renderDetailsKey: function (subscription) {
			return current.$super('generateCarousel')(subscription, [
				['service:req:squash:project', current.renderKey(subscription)],
				['name', subscription.data.project.name || subscription.parameters['service:req:squash:project']]
			], 1);
		},

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:req:squash:project', 'service/req/squash/');
		}
	};
	return current;
});
