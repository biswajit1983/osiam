/*
 * Copyright (C) 2013 tarent AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.osiam.resources.controller;

import org.osiam.auth.token.TokenService;
import org.osiam.resources.helper.AttributesRemovalHelper;
import org.osiam.resources.provisioning.SCIMUserProvisioning;
import org.osiam.resources.scim.SCIMSearchResult;
import org.osiam.resources.scim.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * This Controller is used to manage User
 * <p>
 * http://tools.ietf.org/html/draft-ietf-scim-core-schema-00#section-6
 * <p>
 * it is based on the SCIM 2.0 API Specification:
 * <p>
 * http://tools.ietf.org/html/draft-ietf-scim-api-00#section-3
 */
@RestController
@RequestMapping(value = "/Users")
@Transactional
public class UserController {

    @Autowired
    private SCIMUserProvisioning scimUserProvisioning;

    @Autowired
    private TokenService tokenService;

    private AttributesRemovalHelper attributesRemovalHelper = new AttributesRemovalHelper();

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public User getUser(@PathVariable String id) {
        return scimUserProvisioning.getById(id);
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<User> create(@RequestBody @Valid User user, UriComponentsBuilder builder) throws IOException {
        User createdUser = scimUserProvisioning.create(user);
        return buildResponseWithLocation(createdUser, builder, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public ResponseEntity<User> replace(@PathVariable String id, @RequestBody @Valid User user, UriComponentsBuilder builder)
            throws IOException {
        User createdUser = scimUserProvisioning.replace(id, user);
        checkAndHandleDeactivation(id, user);
        return buildResponseWithLocation(createdUser, builder, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<User> update(@PathVariable String id, @RequestBody User user, UriComponentsBuilder builder)
            throws IOException {
        User createdUser = scimUserProvisioning.update(id, user);
        checkAndHandleDeactivation(id, user);
        return buildResponseWithLocation(createdUser, builder, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    public void delete(@PathVariable final String id) {
        tokenService.revokeAllTokensOfUser(id);
        scimUserProvisioning.delete(id);
    }

    @RequestMapping(method = RequestMethod.GET)
    public SCIMSearchResult<User> searchWithGet(@RequestParam Map<String, String> requestParameters) {
        return searchWithPost(requestParameters);
    }

    @RequestMapping(value = "/.search", method = RequestMethod.POST)
    public SCIMSearchResult<User> searchWithPost(@RequestParam Map<String, String> requestParameters) {
        SCIMSearchResult<User> scimSearchResult = scimUserProvisioning.search(
                requestParameters.get("filter"),
                requestParameters.get("sortBy"),
                requestParameters.getOrDefault("sortOrder", "ascending"),             // scim default
                Integer.parseInt(requestParameters.getOrDefault("count", "" + SCIMSearchResult.MAX_RESULTS)),
                Integer.parseInt(requestParameters.getOrDefault("startIndex", "1"))); // scim default

        return attributesRemovalHelper.removeSpecifiedUserAttributes(scimSearchResult, requestParameters);
    }

    /*
     * Checks whether the given user was deactivated and performs necessary subsequent actions.
     */
    private void checkAndHandleDeactivation(String id, User updateUser) {
        if (updateUser.isActive() != null && !updateUser.isActive()) {
            tokenService.revokeAllTokensOfUser(id);
        }
    }

    private ResponseEntity<User> buildResponseWithLocation(User group, UriComponentsBuilder builder, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        URI location = builder.path("/Users/{id}").buildAndExpand(group.getId()).toUri();
        headers.setLocation(location);
        return new ResponseEntity<>(group, headers, status);
    }
}
